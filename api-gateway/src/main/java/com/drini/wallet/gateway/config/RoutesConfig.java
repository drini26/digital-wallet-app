package com.drini.wallet.gateway.config;

import com.drini.wallet.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.setRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

/**
 * Gateway routes (Gateway Server MVC on virtual threads): which service answers which path.
 */
@Configuration
public class RoutesConfig {

    private static final Logger log = LoggerFactory.getLogger(RoutesConfig.class);

    @Bean
    public RouterFunction<ServerResponse> routes(@Value("${wallet.gateway.auth-uri}") String authUri,
                                                 @Value("${wallet.gateway.ledger-uri}") String ledgerUri,
                                                 @Value("${wallet.gateway.banking-uri}") String bankingUri) {
        // auth-service: sign up, magic links, login, users.
        RouterFunction<ServerResponse> auth = route("auth")
                .route(path("/api/v1/auth/**", "/api/v1/users/**"), http(authUri))
                .before(setRequestHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .onError(ResourceAccessException.class, RoutesConfig::serviceDidNotAnswer)
                .build();

        // banking-service: bank transfers, batches, mock bank.
        // Before the ledger route, so /admin/banking/batches never matches it.
        RouterFunction<ServerResponse> banking = route("banking")
                .route(path("/api/v1/bank/**",
                        "/api/v1/admin/banking/batches/**",
                        "/api/v1/admin/mock-bank/**"), http(bankingUri))
                .before(setRequestHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .onError(ResourceAccessException.class, RoutesConfig::serviceDidNotAnswer)
                .build();

        // ledger-service: wallets, payments and all admin money operations.
        RouterFunction<ServerResponse> ledger = route("ledger")
                .route(path("/api/v1/wallet/**",
                        "/api/v1/transactions/**",
                        "/api/v1/admin/ledger/**",
                        "/api/v1/admin/banking/sweep",
                        "/api/v1/admin/metrics",
                        "/api/v1/admin/errors",
                        "/api/v1/admin/system/**",
                        "/api/v1/admin/transactions/**"), http(ledgerUri))
                .before(setRequestHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .onError(ResourceAccessException.class, RoutesConfig::serviceDidNotAnswer)
                .build();

        return auth.and(banking).and(ledger);
    }

    private static ServerResponse serviceDidNotAnswer(Throwable error, ServerRequest request) {
        log.warn("No answer from the service for {}: {}", request.path(), error.getMessage());
        ErrorCode code = ErrorCode.SERVICE_UNAVAILABLE;
        ProblemDetail body = ProblemDetail.forStatus(code.getStatus());
        body.setTitle(code.getTitle());
        body.setDetail("The service did not answer. Please try again.");
        body.setProperty("code", code.name());
        return ServerResponse.status(code.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}
