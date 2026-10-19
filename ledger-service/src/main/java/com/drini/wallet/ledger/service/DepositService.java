package com.drini.wallet.ledger.service;

import com.drini.wallet.ledger.domain.Account;
import com.drini.wallet.ledger.domain.LedgerTransaction;
import com.drini.wallet.ledger.domain.PostingLine;
import com.drini.wallet.ledger.domain.SystemAccounts;
import com.drini.wallet.ledger.domain.TransactionType;
import com.drini.wallet.ledger.repository.LedgerTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Puts money that arrived at our bank into a wallet: DEBIT OPERATING_BANK (our cash grows), CREDIT the wallet (what
 * we owe the user grows).
 */
@Service
public class DepositService {

    private final LedgerTransactionRepository transactions;
    private final LedgerPostingService posting;

    public DepositService(LedgerTransactionRepository transactions, LedgerPostingService posting) {
        this.transactions = transactions;
        this.posting = posting;
    }

    @Transactional
    public LedgerTransaction deposit(UUID walletId, BigDecimal amount, String externalReference, String description) {
        Optional<LedgerTransaction> existing =
                transactions.findByTypeAndExternalReference(TransactionType.DEPOSIT, externalReference);
        if (existing.isPresent()) {
            return existing.get();
        }

        Map<UUID, Account> locked = posting.lockInIdOrder(List.of(walletId, SystemAccounts.OPERATING_BANK));
        LedgerTransaction deposit = new LedgerTransaction(TransactionType.DEPOSIT, amount);
        deposit.setExternalReference(externalReference);
        deposit.setPayeeAccountId(walletId);
        deposit.setDescription(description);
        return posting.post(deposit, List.of(
                PostingLine.debit(SystemAccounts.OPERATING_BANK, amount),
                PostingLine.credit(walletId, amount)), locked);
    }
}
