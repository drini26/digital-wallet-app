package com.drini.wallet.ledger.support;

import com.drini.wallet.ledger.client.BankingClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.containers.PostgreSQLContainer;

/** Shared setup for the ledger integration tests. */
@TestConfiguration(proxyBeanMethods = false)
public class LedgerTestConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    @Bean
    @Primary
    public BankingClient mockBankingClient() {
        return Mockito.mock(BankingClient.class);
    }

    @Bean
    @Primary
    public JavaMailSender mockMailSender() {
        return Mockito.mock(JavaMailSender.class);
    }
}
