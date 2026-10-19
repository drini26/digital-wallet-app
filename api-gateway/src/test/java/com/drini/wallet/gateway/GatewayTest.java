package com.drini.wallet.gateway;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests the gateway with three tiny fake services (one per real service, plain JDK HttpServer). */
@SpringBootTest(properties = {
        "wallet.gateway.sign-in-requests-per-minute=3",
        "spring.http.client.read-timeout=1s"})
@AutoConfigureMockMvc
class GatewayTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-characters-long";

    private static final HttpServer AUTH = startFakeService("auth");
    private static final HttpServer LEDGER = startFakeService("ledger");
    private static final HttpServer BANKING = startFakeService("banking");

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void serviceUrls(DynamicPropertyRegistry registry) {
        registry.add("wallet.gateway.auth-uri", () -> "http://127.0.0.1:" + AUTH.getAddress().getPort());
        registry.add("wallet.gateway.ledger-uri", () -> "http://127.0.0.1:" + LEDGER.getAddress().getPort());
        registry.add("wallet.gateway.banking-uri", () -> "http://127.0.0.1:" + BANKING.getAddress().getPort());
        registry.add("wallet.gateway.jwt-secret", () -> SECRET);
    }

    @AfterAll
    static void stopFakeServices() {
        AUTH.stop(0);
        LEDGER.stop(0);
        BANKING.stop(0);
    }

    @Test
    void loginIsPublicAndGoesToAuth() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("auth"));
    }

    @Test
    void customerRequestsAreRoutedToTheRightService() throws Exception {
        String customer = token("CUSTOMER", 60);

        getWithToken("/api/v1/wallet/balance", customer).andExpect(status().isOk()).andExpect(jsonPath("$.service").value("ledger"));
        getWithToken("/api/v1/bank/transfers", customer).andExpect(status().isOk()).andExpect(jsonPath("$.service").value("banking"));
        getWithToken("/api/v1/users/me", customer).andExpect(status().isOk()).andExpect(jsonPath("$.service").value("auth"));
    }

    @Test
    void managerRequestsAreRoutedToTheRightService() throws Exception {
        String manager = token("MANAGER", 60);

        getWithToken("/api/v1/admin/metrics", manager).andExpect(jsonPath("$.service").value("ledger"));
        getWithToken("/api/v1/admin/system/status", manager).andExpect(jsonPath("$.service").value("ledger"));
        getWithToken("/api/v1/admin/errors", manager).andExpect(jsonPath("$.service").value("ledger"));
        getWithToken("/api/v1/admin/banking/batches/run", manager).andExpect(jsonPath("$.service").value("banking"));
        getWithToken("/api/v1/admin/mock-bank/settings", manager).andExpect(jsonPath("$.service").value("banking"));
        mvc.perform(post("/api/v1/admin/banking/sweep").header("Authorization", "Bearer " + manager))
                .andExpect(jsonPath("$.service").value("ledger"))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/banking/sweep"));
    }

    @Test
    void customerCannotCallAdminEndpoints() throws Exception {
        getWithToken("/api/v1/admin/metrics", token("CUSTOMER", 60))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void missingExpiredOrForgedTokenIsRefused() throws Exception {
        mvc.perform(get("/api/v1/wallet/balance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        getWithToken("/api/v1/wallet/balance", token("CUSTOMER", -60)).andExpect(status().isUnauthorized());
        getWithToken("/api/v1/wallet/balance", "not.a.jwt").andExpect(status().isUnauthorized());
    }

    @Test
    void internalAndActuatorPathsAreBlocked() throws Exception {
        String manager = token("MANAGER", 60);

        getWithToken("/internal/holds", manager).andExpect(status().isForbidden());
        getWithToken("/actuator/env", manager).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void tooManySignInRequestsAreLimited() throws Exception {
        for (int i = 0; i < 3; i++) {
            requestLink("10.0.0.99").andExpect(status().isOk());
        }
        requestLink("10.0.0.99")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        requestLink("10.0.0.100").andExpect(status().isOk());
    }

    @Test
    void slowServiceAnswersServiceUnavailable() throws Exception {
        getWithToken("/api/v1/wallet/slow", token("CUSTOMER", 60))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    private ResultActions getWithToken(String path, String jwt) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + jwt));
    }

    private ResultActions requestLink(String clientIp) throws Exception {
        return mvc.perform(post("/api/v1/auth/magic-link").header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private static String token(String role, long lifetimeSeconds) {
        SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(now.minusSeconds(120))
                .expiresAt(now.plusSeconds(lifetimeSeconds))
                .claim("role", role)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static HttpServer startFakeService(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> answer(exchange, name));
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void answer(HttpExchange exchange, String name) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.endsWith("/slow")) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        exchange.getRequestBody().readAllBytes();
        byte[] body = ("{\"service\":\"" + name + "\",\"path\":\"" + path + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } catch (IOException gatewayAlreadyGaveUp) {
        }
        exchange.close();
    }
}
