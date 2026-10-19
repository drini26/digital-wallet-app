package com.drini.wallet.auth.client;

import com.drini.wallet.common.http.InternalClientRetry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Checks the call auth -> ledger POST /internal/wallets with a fake ledger (MockRestServiceServer). */
class LedgerWalletClientTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer ledger = MockRestServiceServer.bindTo(builder).build();
    private final LedgerWalletClient client =
            new LedgerWalletClient(builder, new InternalClientRetry(), "http://ledger", "the-key");

    @Test
    void createsTheWallet() {
        UUID ownerId = UUID.randomUUID();
        ledger.expect(requestTo("http://ledger/internal/wallets"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "the-key"))
                .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.accountType").value("MERCHANT"))
                .andRespond(withSuccess());

        assertThat(client.createWallet(ownerId, "MERCHANT", "Kafe")).isTrue();
        ledger.verify();
    }

    @Test
    void ledgerDownDoesNotBreakSignUp() {
        ledger.expect(ExpectedCount.times(4), requestTo("http://ledger/internal/wallets"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(client.createWallet(UUID.randomUUID(), "PERSONAL", "Ana")).isFalse();
        ledger.verify();
    }

    @Test
    void ledgerRefusalDoesNotBreakSignUp() {
        ledger.expect(ExpectedCount.once(), requestTo("http://ledger/internal/wallets"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(client.createWallet(UUID.randomUUID(), "PERSONAL", "Ana")).isFalse();
        ledger.verify();
    }
}
