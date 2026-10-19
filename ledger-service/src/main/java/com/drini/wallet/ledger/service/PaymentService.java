package com.drini.wallet.ledger.service;

import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.common.money.Money;
import com.drini.wallet.common.security.CurrentUser;
import com.drini.wallet.ledger.api.dto.PayRequest;
import com.drini.wallet.ledger.api.dto.PaymentResponse;
import com.drini.wallet.ledger.domain.Account;
import com.drini.wallet.ledger.domain.LedgerTransaction;
import com.drini.wallet.ledger.domain.PostingLine;
import com.drini.wallet.ledger.domain.TransactionType;
import com.drini.wallet.ledger.repository.AccountRepository;
import com.drini.wallet.ledger.repository.LedgerTransactionRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Pays another user or a merchant instantly. */
@Service
public class PaymentService {

    private final EntityManager entityManager;
    private final WalletService wallets;
    private final AccountRepository accounts;
    private final LedgerTransactionRepository transactions;
    private final LedgerPostingService posting;
    private final PaymentRules rules;

    public PaymentService(EntityManager entityManager, WalletService wallets, AccountRepository accounts,
                          LedgerTransactionRepository transactions, LedgerPostingService posting, PaymentRules rules) {
        this.entityManager = entityManager;
        this.wallets = wallets;
        this.accounts = accounts;
        this.transactions = transactions;
        this.posting = posting;
        this.rules = rules;
    }

    @Transactional
    public PaymentResponse pay(CurrentUser payer, PayRequest request, String idempotencyKey) {
        long startedAt = System.nanoTime();
        BigDecimal amount = Money.requirePositiveAmount(request.getAmount());

        // A busy wallet answers 503 after 2 seconds instead of hanging.
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '2s'").executeUpdate();

        UUID payerWalletId = wallets.createWalletIfMissing(payer.getUserId(), payer.getAccountType(), payer.getName());
        UUID payeeWalletId = accounts.findWalletIdByOwnerId(request.getRecipientId())
                .orElseThrow(() -> new WalletException(ErrorCode.RECIPIENT_NOT_FOUND, "The recipient has no wallet"));

        // Lock both wallets in id order; balances are only read under the lock.
        Map<UUID, Account> locked = posting.lockInIdOrder(List.of(payerWalletId, payeeWalletId));
        Account payerWallet = locked.get(payerWalletId);
        Account payeeWallet = locked.get(payeeWalletId);

        // Checked after the lock: a concurrent duplicate waits, then replays the first result.
        Optional<LedgerTransaction> previous =
                transactions.findByInitiatedByAndIdempotencyKey(payer.getUserId(), idempotencyKey);
        if (previous.isPresent()) {
            return replay(previous.get(), payeeWalletId, amount, request);
        }

        rules.check(request.getCurrency(), amount, request.getTransactionType(), payerWallet, payeeWallet);

        LedgerTransaction payment = new LedgerTransaction(TransactionType.PAYMENT, amount);
        payment.setPaymentType(request.getTransactionType());
        payment.setInitiatedBy(payer.getUserId());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setPayerAccountId(payerWalletId);
        payment.setPayeeAccountId(payeeWalletId);
        payment.setDescription(request.getDescription());
        posting.post(payment, List.of(
                PostingLine.debit(payerWalletId, amount),
                PostingLine.credit(payeeWalletId, amount)), locked);

        payment.setResultingPayerBalance(payerWallet.getBalance());
        payment.setProcessingMs((int) ((System.nanoTime() - startedAt) / 1_000_000));
        return toResponse(payment, request.getRecipientId(), false);
    }

    private PaymentResponse replay(LedgerTransaction previous, UUID payeeWalletId, BigDecimal amount,
                                   PayRequest request) {
        boolean samePayment = payeeWalletId.equals(previous.getPayeeAccountId())
                && amount.compareTo(previous.getAmount()) == 0
                && request.getTransactionType() == previous.getPaymentType();
        if (!samePayment) {
            throw new WalletException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This Idempotency-Key was already used for a different payment");
        }
        return toResponse(previous, request.getRecipientId(), true);
    }

    private PaymentResponse toResponse(LedgerTransaction payment, UUID recipientId, boolean replayed) {
        PaymentResponse response = new PaymentResponse();
        response.setTransactionId(payment.getId());
        response.setStatus(payment.getStatus());
        response.setTimestamp(payment.getCreatedAt());
        response.setTransactionType(payment.getPaymentType().name());
        response.setRecipientId(recipientId);
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        response.setAvailableBalance(payment.getResultingPayerBalance());
        response.setReplayed(replayed);
        return response;
    }
}
