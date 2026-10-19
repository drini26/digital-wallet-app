package com.drini.wallet.ledger.client;

import com.drini.wallet.common.http.InternalClientRetry;
import com.drini.wallet.common.security.InternalApiKeyFilter;
import com.drini.wallet.ledger.client.dto.BankBalances;
import com.drini.wallet.ledger.client.dto.BankSweepRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

/** The calls the ledger makes on banking-service. */
@Component
public class BankingClient {

    private final RestClient restClient;
    private final InternalClientRetry retry;

    public BankingClient(RestClient.Builder builder, InternalClientRetry retry,
                         @Value("${wallet.banking.base-url}") String bankingBaseUrl,
                         @Value("${wallet.internal-api-key}") String internalApiKey) {
        this.restClient = builder
                .baseUrl(bankingBaseUrl)
                .defaultHeader(InternalApiKeyFilter.HEADER, internalApiKey)
                .build();
        this.retry = retry;
    }

    public BankBalances getBankBalances() {
        return retry.call("banking: bank balances", () -> restClient.get()
                .uri("/internal/bank-balances")
                .retrieve()
                .body(BankBalances.class));
    }

    public void sweep(UUID sweepId, BigDecimal amount) {
        BankSweepRequest body = new BankSweepRequest();
        body.setSweepId(sweepId);
        body.setAmount(amount);
        retry.call("banking: sweep", () -> restClient.post()
                .uri("/internal/sweeps")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }
}
