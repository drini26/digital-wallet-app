package com.drini.wallet.auth.support;

import com.drini.wallet.auth.client.LedgerWalletClient;
import com.drini.wallet.auth.notification.LinkSender;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;

/** Shared setup for the auth-service integration tests. */
@TestConfiguration(proxyBeanMethods = false)
public class AuthTestConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    @Bean
    @Primary
    public LinkSender mockLinkSender() {
        return Mockito.mock(LinkSender.class);
    }

    @Bean
    @Primary
    public LedgerWalletClient mockLedgerWalletClient() {
        return Mockito.mock(LedgerWalletClient.class);
    }
}
