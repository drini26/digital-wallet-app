package com.drini.wallet.ledger;

import com.drini.wallet.common.http.InternalClientRetry;
import com.drini.wallet.ledger.client.BankingClient;
import com.drini.wallet.ledger.client.dto.BankBalances;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Checks the two calls the ledger makes on banking, with a fake banking server (MockRestServiceServer). */
class BankingClientTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer bankingServer = MockRestServiceServer.bindTo(builder).build();
    private final BankingClient client = new BankingClient(builder, new InternalClientRetry(), "http://banking", "the-key");

    @Test
    void readsBankBalances() {
        bankingServer.expect(requestTo("http://banking/internal/bank-balances"))
                .andExpect(header("X-Internal-Api-Key", "the-key"))
                .andRespond(withSuccess("{\"operatingBalance\":1500.00,\"partnerBalance\":0,\"pendingLedgerNotifications\":0}",
                        MediaType.APPLICATION_JSON));

        BankBalances balances = client.getBankBalances();

        assertThat(balances.getOperatingBalance()).isEqualByComparingTo("1500.00");
    }

    @Test
    void sendsTheSweep() {
        UUID sweepId = UUID.randomUUID();
        bankingServer.expect(requestTo("http://banking/internal/sweeps"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.sweepId").value(sweepId.toString()))
                .andExpect(jsonPath("$.amount").value(250.00))
                .andRespond(withNoContent());

        client.sweep(sweepId, new BigDecimal("250.00"));
        bankingServer.verify();
    }
}
