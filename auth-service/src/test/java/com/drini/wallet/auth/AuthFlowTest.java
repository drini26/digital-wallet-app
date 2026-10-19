package com.drini.wallet.auth;

import com.drini.wallet.auth.client.LedgerWalletClient;
import com.drini.wallet.auth.domain.Identifier;
import com.drini.wallet.auth.domain.LinkPurpose;
import com.drini.wallet.auth.domain.User;
import com.drini.wallet.auth.notification.LinkSender;
import com.drini.wallet.auth.repository.UserRepository;
import com.drini.wallet.auth.seed.DemoUsersSeed;
import com.drini.wallet.auth.support.AuthTestConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end tests of auth-service through its HTTP API with a real PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthTestConfig.class)
class AuthFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private LinkSender linkSender;
    @Autowired
    private LedgerWalletClient ledger;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(linkSender, ledger);
    }

    @Test
    void newPersonalUserCanSignUp() throws Exception {
        String email = randomEmail();

        register("Dora Customer", email, "PERSONAL")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists());

        User user = users.findByEmail(email).orElseThrow();
        assertThat(user.getRole().name()).isEqualTo("CUSTOMER");
        assertThat(user.getAccountType().name()).isEqualTo("PERSONAL");
        verify(ledger).createWallet(user.getId(), "PERSONAL", "Dora Customer");
        verify(linkSender).send(any(Identifier.class), anyString(), eq(LinkPurpose.WELCOME));
    }

    @Test
    void newMerchantCanSignUp() throws Exception {
        String email = randomEmail();

        register("Furra Merchant", email, "MERCHANT").andExpect(status().isAccepted());

        assertThat(users.findByEmail(email).orElseThrow().getAccountType().name()).isEqualTo("MERCHANT");
    }

    @Test
    void existingEmailGetsTheSameAnswer() throws Exception {
        String email = randomEmail();
        String firstAnswer = register("Erion", email, "PERSONAL").andReturn().getResponse().getContentAsString();
        Mockito.reset(linkSender, ledger);

        String secondAnswer = register("Someone Else", email.toUpperCase(), "MERCHANT")
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(secondAnswer).isEqualTo(firstAnswer);
        assertThat(users.findByEmail(email).orElseThrow().getDisplayName()).isEqualTo("Erion");
        verify(linkSender).send(any(Identifier.class), anyString(), eq(LinkPurpose.ALREADY_REGISTERED));
        verifyNoInteractions(ledger);
    }

    @Test
    void tooShortNameIsRefused() throws Exception {
        register("A", randomEmail(), "PERSONAL")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void invalidIdentifierIsRefused() throws Exception {
        register("Gent", "not an address", "PERSONAL")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownEmailGetsNoLink() throws Exception {
        requestLink(randomEmail()).andExpect(status().isAccepted());

        verifyNoInteractions(linkSender);
    }

    @Test
    void userCanSignInWithMagicLink() throws Exception {
        String email = randomEmail();
        register("Hana", email, "PERSONAL");

        String token = requestLinkAndCaptureToken(email);
        MvcResult login = login(email, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.accountType").value("PERSONAL"))
                .andReturn();
        String jwt = readJson(login).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Hana"));
    }

    @Test
    void magicLinkIsSingleUse() throws Exception {
        String email = randomEmail();
        register("Ilir", email, "PERSONAL");
        String token = requestLinkAndCaptureToken(email);

        login(email, token).andExpect(status().isOk());
        login(email, token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MAGIC_LINK_USED"));
    }

    @Test
    void expiredLinkIsRefused() throws Exception {
        String email = randomEmail();
        register("Jona", email, "PERSONAL");
        String token = requestLinkAndCaptureToken(email);
        UUID userId = users.findByEmail(email).orElseThrow().getId();
        jdbc.update("UPDATE magic_link_tokens SET expires_at = now() - interval '1 minute' WHERE user_id = ?", userId);

        login(email, token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MAGIC_LINK_EXPIRED"));
    }

    @Test
    void linkOnlyWorksWithItsOwnIdentifier() throws Exception {
        String email = randomEmail();
        register("Klea", email, "PERSONAL");
        String token = requestLinkAndCaptureToken(email);

        login(randomEmail(), token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MAGIC_LINK_INVALID"));
        login(email, "made-up-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MAGIC_LINK_INVALID"));
    }

    @Test
    void phoneSignInRoundTrip() throws Exception {
        String digits = String.valueOf(100000000L + (long) (Math.random() * 899999999L));
        register("Leka Phone", "+355 " + digits, "PERSONAL").andExpect(status().isAccepted());

        String token = requestLinkAndCaptureToken("+355" + digits);
        login("+355" + digits, token).andExpect(status().isOk());

        assertThat(users.findByPhone("+355" + digits)).isPresent();
    }

    @Test
    void lookupFindsRecipients() throws Exception {
        String email = randomEmail();
        register("Mira Merchant", email, "MERCHANT");
        String jwt = signIn(email);

        mockMvc.perform(get("/api/v1/users/lookup").param("identifier", email).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Mira Merchant"))
                .andExpect(jsonPath("$.accountType").value("MERCHANT"));
        mockMvc.perform(get("/api/v1/users/lookup").param("identifier", randomEmail()).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPIENT_NOT_FOUND"));
    }

    @Test
    void userCanChangeTheirDisplayName() throws Exception {
        String email = randomEmail();
        register("Ana Old", email, "PERSONAL");
        String jwt = signIn(email);

        mockMvc.perform(put("/api/v1/users/me").header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"  Ana New  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ana New"))
                .andExpect(jsonPath("$.email").value(email));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + jwt))
                .andExpect(jsonPath("$.displayName").value("Ana New"));

        mockMvc.perform(put("/api/v1/users/me").header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/users/me").header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nobody\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userEndpointsNeedAToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void blockedUserGetsNoLink() throws Exception {
        String email = randomEmail();
        register("Nora", email, "PERSONAL");
        jdbc.update("UPDATE users SET status = 'BLOCKED' WHERE email = ?", email);
        Mockito.reset(linkSender);

        requestLink(email).andExpect(status().isAccepted());

        verify(linkSender, never()).send(any(), anyString(), any());
    }

    @Test
    void demoSeedCreatesTheDemoUsers() {
        DemoUsersSeed seed = new DemoUsersSeed(users);

        seed.run(null);
        seed.run(null);

        assertThat(users.findById(DemoUsersSeed.ELIRA_ID).orElseThrow().getRole().name()).isEqualTo("MANAGER");
        assertThat(users.findById(DemoUsersSeed.KAFE_ID).orElseThrow().getAccountType().name()).isEqualTo("MERCHANT");
        assertThat(users.findByEmail("ana@wallet.local")).isPresent();
        assertThat(users.findByEmail("besi@wallet.local")).isPresent();
    }

    private String signIn(String identifier) throws Exception {
        String token = requestLinkAndCaptureToken(identifier);
        return readJson(login(identifier, token).andReturn()).get("accessToken").asText();
    }

    private String requestLinkAndCaptureToken(String identifier) throws Exception {
        Mockito.reset(linkSender);
        requestLink(identifier).andExpect(status().isAccepted());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(linkSender).send(any(Identifier.class), token.capture(), eq(LinkPurpose.SIGN_IN));
        return token.getValue();
    }

    private org.springframework.test.web.servlet.ResultActions register(String name, String identifier, String type)
            throws Exception {
        String body = "{\"displayName\":\"" + name + "\",\"identifier\":\"" + identifier + "\",\"accountType\":\"" + type + "\"}";
        return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions requestLink(String identifier) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/magic-link").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + identifier + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions login(String identifier, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + identifier + "\",\"token\":\"" + token + "\"}"));
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String randomEmail() {
        return "user-" + UUID.randomUUID() + "@test.local";
    }
}
