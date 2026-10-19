package com.drini.wallet.banking.service;

import com.drini.wallet.banking.api.dto.CreateTransferRequest;
import com.drini.wallet.banking.api.dto.TransferResponse;
import com.drini.wallet.banking.client.LedgerClient;
import com.drini.wallet.banking.client.dto.LedgerWallet;
import com.drini.wallet.banking.domain.BankTransfer;
import com.drini.wallet.banking.domain.RoutingNumber;
import com.drini.wallet.banking.domain.TransferStatus;
import com.drini.wallet.banking.repository.BankTransferRepository;
import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.common.money.Money;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Creates bank transfers and lists a user's transfers. */
@Service
public class TransferService {

    private final BankTransferRepository transfers;
    private final LedgerClient ledger;

    public TransferService(BankTransferRepository transfers, LedgerClient ledger) {
        this.transfers = transfers;
        this.ledger = ledger;
    }

    public TransferResponse createTransfer(UUID userId, CreateTransferRequest request, String idempotencyKey) {
        BigDecimal amount = Money.requirePositiveAmount(request.getAmount());
        if (!Money.EUR.equals(request.getCurrency())) {
            throw new WalletException(ErrorCode.UNSUPPORTED_CURRENCY, "Only EUR is supported");
        }
        if (!RoutingNumber.isValid(request.getBankRoutingNumber())) {
            throw new WalletException(ErrorCode.INVALID_ROUTING_NUMBER, "The bank routing number is not valid");
        }

        Optional<BankTransfer> existing = transfers.findByOwnerIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent() && existing.get().getStatus() != TransferStatus.REQUESTED) {
            return toResponse(existing.get());
        }

        BankTransfer transfer;
        if (existing.isPresent()) {
            transfer = existing.get();
        } else {
            // The wallet id comes from the request body, so check that it belongs to the caller.
            LedgerWallet wallet = ledger.getWallet(request.getSourceWalletId());
            if (!userId.equals(wallet.getOwnerId())) {
                throw new WalletException(ErrorCode.WALLET_NOT_FOUND, "Wallet not found");
            }
            transfer = transfers.save(new BankTransfer(userId, wallet.getWalletId(), request.getTransferDirection(),
                    amount, request.getBankRoutingNumber(), request.getExternalAccountNumber(), idempotencyKey));
        }

        LocalDate estimatedArrival = BusinessDays.estimatedArrival(LocalDate.now());
        try {
            if (transfer.isOutflow()) {
                ledger.hold(transfer, estimatedArrival);
            } else {
                ledger.incoming(transfer, estimatedArrival);
            }
        } catch (WalletException e) {
            if (e.getErrorCode() == ErrorCode.SERVICE_UNAVAILABLE) {
                throw new WalletException(ErrorCode.SERVICE_UNAVAILABLE,
                        "The ledger is not reachable. Retry with the same Idempotency-Key.");
            }
            transfer.markFailed(e.getMessage());
            transfers.save(transfer);
            throw e;
        }

        transfer.markQueued(estimatedArrival);
        return toResponse(transfers.save(transfer));
    }

    public List<TransferResponse> listTransfers(UUID userId) {
        List<TransferResponse> responses = new ArrayList<>();
        for (BankTransfer transfer : transfers.findTop50ByOwnerIdOrderByCreatedAtDesc(userId)) {
            responses.add(toResponse(transfer));
        }
        return responses;
    }

    static TransferResponse toResponse(BankTransfer transfer) {
        TransferResponse response = new TransferResponse();
        response.setTrackingId(transfer.getId());
        response.setTransferDirection(transfer.getDirection().name());
        response.setAmount(transfer.getAmount());
        response.setCurrency(transfer.getCurrency());
        response.setStatus(transfer.getStatus().name());
        response.setEstimatedArrivalDate(transfer.getEstimatedArrival());
        response.setMaskedAccountNumber(mask(transfer.getExternalAccountNumber()));
        response.setAttemptCount(transfer.getAttemptCount());
        response.setLastError(transfer.getLastError());
        response.setCreatedAt(transfer.getCreatedAt());
        return response;
    }

    static String mask(String accountNumber) {
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
