package com.drini.wallet.ledger.service;

import com.drini.wallet.ledger.domain.Account;
import com.drini.wallet.ledger.domain.Direction;
import com.drini.wallet.ledger.domain.LedgerEntry;
import com.drini.wallet.ledger.domain.LedgerTransaction;
import com.drini.wallet.ledger.domain.PostingLine;
import com.drini.wallet.ledger.repository.LedgerEntryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The only place that writes double-entry ledger entries and changes account balances. */
@Service
public class LedgerPostingService {

    private final EntityManager entityManager;
    private final LedgerEntryRepository entries;

    public LedgerPostingService(EntityManager entityManager, LedgerEntryRepository entries) {
        this.entityManager = entityManager;
        this.entries = entries;
    }

    /**
     * Locks the accounts (SELECT ... FOR UPDATE) sorted by id, so two opposite payments wait instead of deadlocking.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<UUID, Account> lockInIdOrder(Collection<UUID> accountIds) {
        List<UUID> sortedIds = new ArrayList<>(new HashSet<>(accountIds));
        sortedIds.sort(Comparator.comparing(UUID::toString));

        Map<UUID, Account> locked = new LinkedHashMap<>();
        for (UUID id : sortedIds) {
            Account account = entityManager.find(Account.class, id);
            if (account == null) {
                throw new IllegalStateException("Account " + id + " does not exist");
            }
            entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
            locked.put(id, account);
        }
        return locked;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction post(LedgerTransaction transaction, List<PostingLine> lines,
                                  Map<UUID, Account> lockedAccounts) {
        // Debits must equal credits. A deferred database trigger checks this again at commit.
        checkBalanced(lines);

        for (PostingLine line : lines) {
            if (!lockedAccounts.containsKey(line.getAccountId())) {
                throw new IllegalStateException("Account " + line.getAccountId() + " must be locked before posting");
            }
        }

        // persist(), not save(): ids are assigned in Java, so save() would merge into a detached copy.
        entityManager.persist(transaction);
        entityManager.flush();
        for (PostingLine line : lines) {
            entries.save(new LedgerEntry(transaction.getId(), line.getAccountId(), line.getDirection(),
                    line.getAmount(), transaction.getCurrency()));
        }

        for (PostingLine line : lines) {
            lockedAccounts.get(line.getAccountId()).applyEntry(line.getDirection(), line.getAmount());
        }
        return transaction;
    }

    public static void checkBalanced(List<PostingLine> lines) {
        if (lines.size() < 2) {
            throw new IllegalArgumentException("A posting needs at least one debit and one credit");
        }
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (PostingLine line : lines) {
            if (line.getAmount().signum() <= 0) {
                throw new IllegalArgumentException("Posting amounts must be positive: " + line.getAmount());
            }
            if (line.getDirection() == Direction.DEBIT) {
                debits = debits.add(line.getAmount());
            } else {
                credits = credits.add(line.getAmount());
            }
        }
        if (debits.compareTo(credits) != 0) {
            throw new IllegalArgumentException("Unbalanced posting: debits " + debits + ", credits " + credits);
        }
    }
}
