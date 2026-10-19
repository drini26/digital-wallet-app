package com.drini.wallet.ledger.service;

import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.ledger.api.dto.ReversalResponse;
import com.drini.wallet.ledger.domain.Account;
import com.drini.wallet.ledger.domain.LedgerTransaction;
import com.drini.wallet.ledger.domain.PostingLine;
import com.drini.wallet.ledger.domain.TransactionType;
import com.drini.wallet.ledger.repository.LedgerTransactionRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A manager undoes a payment (POST /api/v1/admin/transactions/{id}/reverse). */
@Service
public class ReversalService {

    private final EntityManager entityManager;
    private final LedgerTransactionRepository transactions;
    private final LedgerPostingService posting;

    public ReversalService(EntityManager entityManager, LedgerTransactionRepository transactions,
                           LedgerPostingService posting) {
        this.entityManager = entityManager;
        this.transactions = transactions;
        this.posting = posting;
    }

    @Transactional
    public ReversalResponse reverse(UUID transactionId, UUID managerId, String reason) {
        LedgerTransaction original = transactions.findById(transactionId)
                .orElseThrow(() -> new WalletException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found"));
        if (original.getType() != TransactionType.PAYMENT) {
            throw new WalletException(ErrorCode.NOT_REVERSIBLE, "Only payments can be reversed");
        }

        // Checked after the lock, so two simultaneous reversals cannot both pass.
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '2s'").executeUpdate();
        UUID payerWallet = original.getPayerAccountId();
        UUID payeeWallet = original.getPayeeAccountId();
        Map<UUID, Account> locked = posting.lockInIdOrder(List.of(payerWallet, payeeWallet));
        if (transactions.findByReversalOf(transactionId).isPresent()) {
            throw new WalletException(ErrorCode.ALREADY_REVERSED, "This payment was already reversed");
        }

        if (locked.get(payeeWallet).getBalance().compareTo(original.getAmount()) < 0) {
            throw new WalletException(ErrorCode.INSUFFICIENT_FUNDS,
                    "The recipient no longer has enough money to reverse this payment");
        }

        LedgerTransaction reversal = new LedgerTransaction(TransactionType.REVERSAL, original.getAmount());
        reversal.setReversalOf(transactionId);
        reversal.setInitiatedBy(managerId);
        reversal.setPayerAccountId(payeeWallet);
        reversal.setPayeeAccountId(payerWallet);
        reversal.setDescription("Reversal: " + reason);
        posting.post(reversal, List.of(
                PostingLine.debit(payeeWallet, original.getAmount()),
                PostingLine.credit(payerWallet, original.getAmount())), locked);

        ReversalResponse response = new ReversalResponse();
        response.setReversalTransactionId(reversal.getId());
        response.setOriginalTransactionId(transactionId);
        response.setAmount(reversal.getAmount());
        response.setTimestamp(reversal.getCreatedAt());
        return response;
    }
}
