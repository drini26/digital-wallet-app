package com.drini.wallet.ledger.service;

import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.common.money.Money;
import com.drini.wallet.ledger.api.dto.HoldRequest;
import com.drini.wallet.ledger.api.dto.HoldResponse;
import com.drini.wallet.ledger.domain.Account;
import com.drini.wallet.ledger.domain.LedgerTransaction;
import com.drini.wallet.ledger.domain.PendingTransfer;
import com.drini.wallet.ledger.domain.PostingLine;
import com.drini.wallet.ledger.domain.SystemAccounts;
import com.drini.wallet.ledger.domain.TransactionType;
import com.drini.wallet.ledger.repository.AccountRepository;
import com.drini.wallet.ledger.repository.LedgerTransactionRepository;
import com.drini.wallet.ledger.repository.PendingTransferRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Reserves money for a withdrawal (POST /internal/holds from banking-service), so it cannot be spent twice. */
@Service
public class HoldService {

    private final EntityManager entityManager;
    private final LedgerTransactionRepository transactions;
    private final PendingTransferRepository pendingTransfers;
    private final AccountRepository accounts;
    private final LedgerPostingService posting;
    private final SystemControlService systemControl;

    public HoldService(EntityManager entityManager, LedgerTransactionRepository transactions,
                       PendingTransferRepository pendingTransfers, AccountRepository accounts,
                       LedgerPostingService posting, SystemControlService systemControl) {
        this.entityManager = entityManager;
        this.transactions = transactions;
        this.pendingTransfers = pendingTransfers;
        this.accounts = accounts;
        this.posting = posting;
        this.systemControl = systemControl;
    }

    @Transactional
    public HoldResponse hold(HoldRequest request) {
        BigDecimal amount = Money.requirePositiveAmount(request.getAmount());
        String reference = request.getTransferId().toString();

        Optional<LedgerTransaction> existing =
                transactions.findByTypeAndExternalReference(TransactionType.WITHDRAWAL_HOLD, reference);
        if (existing.isPresent()) {
            return toResponse(existing.get(), request.getWalletId());
        }

        systemControl.requireNotFrozen();

        entityManager.createNativeQuery("SET LOCAL lock_timeout = '2s'").executeUpdate();
        UUID walletId = request.getWalletId();
        Map<UUID, Account> locked = posting.lockInIdOrder(List.of(walletId, SystemAccounts.OUTGOING_PENDING));

        if (locked.get(walletId).getBalance().compareTo(amount) < 0) {
            throw new WalletException(ErrorCode.INSUFFICIENT_FUNDS, "Your balance is not enough for this withdrawal");
        }

        LedgerTransaction hold = new LedgerTransaction(TransactionType.WITHDRAWAL_HOLD, amount);
        hold.setExternalReference(reference);
        hold.setPayerAccountId(walletId);
        hold.setDescription("Withdrawal to bank");
        posting.post(hold, List.of(
                PostingLine.debit(walletId, amount),
                PostingLine.credit(SystemAccounts.OUTGOING_PENDING, amount)), locked);

        pendingTransfers.save(new PendingTransfer(request.getTransferId(), walletId, "OUTFLOW", amount,
                request.getEstimatedArrival()));
        return toResponse(hold, walletId);
    }

    private HoldResponse toResponse(LedgerTransaction hold, UUID walletId) {
        HoldResponse response = new HoldResponse();
        response.setTransactionId(hold.getId());
        response.setAvailableBalance(accounts.findById(walletId).orElseThrow().getBalance());
        return response;
    }
}
