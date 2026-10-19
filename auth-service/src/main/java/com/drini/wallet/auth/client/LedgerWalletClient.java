package com.drini.wallet.auth.client;

import com.drini.wallet.auth.client.dto.CreateWalletRequest;
import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.common.http.InternalClientRetry;
import com.drini.wallet.common.security.InternalApiKeyFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/** Asks the ledger to create the wallet of a new user: POST /internal/wallets. */
@Component
public class LedgerWalletClient {

    private static final Logger log = LoggerFactory.getLogger(LedgerWalletClient.class);

    private final RestClient restClient;
    private final InternalClientRetry retry;

    public LedgerWalletClient(RestClient.Builder builder, InternalClientRetry retry,
                              @Value("${wallet.ledger.base-url}") String ledgerBaseUrl,
                              @Value("${wallet.internal-api-key}") String internalApiKey) {
        this.restClient = builder
                .baseUrl(ledgerBaseUrl)
                .defaultHeader(InternalApiKeyFilter.HEADER, internalApiKey)
                .build();
        this.retry = retry;
    }

    public boolean createWallet(UUID ownerId, String accountType, String displayName) {
        CreateWalletRequest body = new CreateWalletRequest();
        body.setOwnerId(ownerId);
        body.setAccountType(accountType);
        body.setDisplayName(displayName);
        try {
            retry.call("ledger: create wallet", () -> restClient.post()
                    .uri("/internal/wallets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity());
            return true;
        } catch (WalletException | RestClientException e) {
            log.warn("Ledger did not create the wallet of user {} ({}); it will be created on first use",
                    ownerId, e.getMessage());
            return false;
        }
    }
}
