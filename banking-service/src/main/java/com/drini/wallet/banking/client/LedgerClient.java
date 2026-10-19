package com.drini.wallet.banking.client;

import com.drini.wallet.banking.client.dto.LedgerHoldRequest;
import com.drini.wallet.banking.client.dto.LedgerSystemStatus;
import com.drini.wallet.banking.client.dto.LedgerTransferNotice;
import com.drini.wallet.banking.client.dto.LedgerWallet;
import com.drini.wallet.banking.domain.BankTransfer;
import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.common.http.InternalClientRetry;
import com.drini.wallet.common.security.InternalApiKeyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

/** All calls banking-service makes on the ledger. */
@Component
public class LedgerClient {

    private final RestClient restClient;
    private final InternalClientRetry retry;

    public LedgerClient(RestClient.Builder builder, InternalClientRetry retry,
                        @Value("${wallet.ledger.base-url}") String ledgerBaseUrl,
                        @Value("${wallet.internal-api-key}") String internalApiKey) {
        this.restClient = builder
                .baseUrl(ledgerBaseUrl)
                .defaultHeader(InternalApiKeyFilter.HEADER, internalApiKey)
                .build();
        this.retry = retry;
    }

    public LedgerWallet getWallet(UUID walletId) {
        return call("ledger: get wallet", () -> restClient.get()
                .uri("/internal/wallets/{walletId}", walletId)
                .retrieve()
                .body(LedgerWallet.class));
    }

    public void hold(BankTransfer transfer, LocalDate estimatedArrival) {
        LedgerHoldRequest body = new LedgerHoldRequest();
        body.setTransferId(transfer.getId());
        body.setWalletId(transfer.getWalletAccountId());
        body.setAmount(transfer.getAmount());
        body.setEstimatedArrival(estimatedArrival);
        call("ledger: hold money", () -> restClient.post()
                .uri("/internal/holds")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    public void incoming(BankTransfer transfer, LocalDate estimatedArrival) {
        sendNotice(transfer, "incoming", estimatedArrival);
    }

    public void settled(BankTransfer transfer) {
        sendNotice(transfer, "settled", transfer.getEstimatedArrival());
    }

    public void failed(BankTransfer transfer) {
        sendNotice(transfer, "failed", transfer.getEstimatedArrival());
    }

    public boolean isOutgoingFrozen() {
        LedgerSystemStatus status = call("ledger: system status", () -> restClient.get()
                .uri("/internal/system-status")
                .retrieve()
                .body(LedgerSystemStatus.class));
        return status.isOutgoingFrozen();
    }

    private void sendNotice(BankTransfer transfer, String what, LocalDate estimatedArrival) {
        LedgerTransferNotice body = new LedgerTransferNotice();
        body.setDirection(transfer.getDirection().name());
        body.setWalletId(transfer.getWalletAccountId());
        body.setAmount(transfer.getAmount());
        body.setEstimatedArrival(estimatedArrival);
        call("ledger: transfer " + what, () -> restClient.post()
                .uri("/internal/transfers/{transferId}/" + what, transfer.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    private <T> T call(String description, Supplier<T> httpCall) {
        try {
            return retry.call(description, httpCall);
        } catch (HttpClientErrorException e) {
            throw toWalletException(e);
        }
    }

    private static WalletException toWalletException(HttpClientErrorException e) {
        ErrorCode code = ErrorCode.fromHttpStatus(e.getStatusCode().value());
        String detail = "The ledger refused the request";
        ProblemDetail problem = e.getResponseBodyAs(ProblemDetail.class);
        if (problem != null && problem.getProperties() != null && problem.getProperties().get("code") != null) {
            String ledgerCode = problem.getProperties().get("code").toString();
            for (ErrorCode candidate : ErrorCode.values()) {
                if (candidate.name().equals(ledgerCode)) {
                    code = candidate;
                }
            }
            detail = problem.getDetail();
        }
        return new WalletException(code, detail);
    }
}
