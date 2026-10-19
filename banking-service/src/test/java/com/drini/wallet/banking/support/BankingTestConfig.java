package com.drini.wallet.banking.support;

import com.drini.wallet.banking.client.LedgerClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;

/** Shared setup for the banking-service integration tests. */
@TestConfiguration(proxyBeanMethods = false)
public class BankingTestConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    @Bean
    @Primary
    public LedgerClient mockLedgerClient() {
        return Mockito.mock(LedgerClient.class);
    }
}
