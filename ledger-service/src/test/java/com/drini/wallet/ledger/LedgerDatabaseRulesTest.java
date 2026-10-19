package com.drini.wallet.ledger;

import com.drini.wallet.ledger.domain.Account;
import com.drini.wallet.ledger.domain.AccountType;
import com.drini.wallet.ledger.domain.Direction;
import com.drini.wallet.ledger.domain.PostingLine;
import com.drini.wallet.ledger.repository.AccountRepository;
import com.drini.wallet.ledger.seed.DemoLedgerSeed;
import com.drini.wallet.ledger.service.DepositService;
import com.drini.wallet.ledger.service.LedgerPostingService;
import com.drini.wallet.ledger.service.WalletService;
import com.drini.wallet.ledger.support.LedgerTestConfig;
import com.drini.wallet.ledger.support.TestWallets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The database protects the books on its own: append-only entries, balanced transactions, no negative balance. */
@SpringBootTest(properties = "wallet.jobs.enabled=false")
@Import(LedgerTestConfig.class)
class LedgerDatabaseRulesTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private TestWallets testWallets;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private WalletService walletService;
    @Autowired
    private DepositService depositService;

    @Test
    void entriesCannotBeUpdated() {
        testWallets.newUser("PERSONAL", "10.00");

        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET amount = amount + 1"))
                .hasMessageContaining("append-only");
    }

    @Test
    void entriesCannotBeDeleted() {
        testWallets.newUser("PERSONAL", "10.00");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_entries"))
                .hasMessageContaining("append-only");
    }

    @Test
    void unbalancedTransactionCannotCommit() {
        UUID walletId = testWallets.walletIdOf(testWallets.newUser("PERSONAL", "0"));
        UUID txId = UUID.randomUUID();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO ledger_transactions (id, type, status, amount, currency) "
                    + "VALUES (?, 'DEPOSIT', 'SUCCESS', 10, 'EUR')", txId);
            jdbc.update("INSERT INTO ledger_entries (transaction_id, account_id, direction, amount, currency) "
                    + "VALUES (?, '00000000-0000-7000-9000-000000000001', 'DEBIT', 10, 'EUR')", txId);
            jdbc.update("INSERT INTO ledger_entries (transaction_id, account_id, direction, amount, currency) "
                    + "VALUES (?, ?, 'CREDIT', 9, 'EUR')", txId, walletId);
        })).hasStackTraceContaining("Unbalanced ledger transaction");

        Integer saved = jdbc.queryForObject("SELECT COUNT(*) FROM ledger_transactions WHERE id = ?", Integer.class, txId);
        assertThat(saved).isZero();
    }

    @Test
    void walletCannotGoNegative() {
        UUID walletId = testWallets.walletIdOf(testWallets.newUser("PERSONAL", "5.00"));

        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET balance = -0.01 WHERE id = ?", walletId))
                .hasMessageContaining("chk_wallet_balance_not_negative");
    }

    @Test
    void javaPostingCheckRefusesBadLines() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertThatThrownBy(() -> checkBalanced(List.of(PostingLine.debit(a, new BigDecimal("10")),
                PostingLine.credit(b, new BigDecimal("9"))))).hasMessageContaining("Unbalanced");
        assertThatThrownBy(() -> checkBalanced(List.of(PostingLine.debit(a, BigDecimal.TEN))))
                .hasMessageContaining("at least one");
        assertThatThrownBy(() -> checkBalanced(List.of(PostingLine.debit(a, BigDecimal.ZERO),
                PostingLine.credit(b, BigDecimal.ZERO)))).hasMessageContaining("positive");
    }

    @Test
    void accountsFollowTheirNormalSide() {
        Account bank = new Account();
        bank.setType(AccountType.OPERATING_BANK);
        bank.setBalance(new BigDecimal("100.00"));
        bank.applyEntry(Direction.DEBIT, new BigDecimal("10.00"));
        Account wallet = new Account();
        wallet.setType(AccountType.PERSONAL_WALLET);
        wallet.setBalance(new BigDecimal("100.00"));
        wallet.applyEntry(Direction.DEBIT, new BigDecimal("10.00"));

        assertThat(bank.getBalance()).isEqualByComparingTo("110.00");
        assertThat(wallet.getBalance()).isEqualByComparingTo("90.00");
    }

    @Test
    void demoSeedIsIdempotent() {
        DemoLedgerSeed demoSeed = new DemoLedgerSeed(walletService, depositService);
        demoSeed.run(null);
        demoSeed.run(null);

        assertThat(testWallets.balanceOf(DemoLedgerSeed.ANA_ID)).isEqualByComparingTo("1000.00");
        assertThat(testWallets.balanceOf(DemoLedgerSeed.BESI_ID)).isEqualByComparingTo("500.00");
        assertThat(testWallets.balanceOf(DemoLedgerSeed.KAFE_ID)).isEqualByComparingTo("0.00");
        UUID kafeWallet = testWallets.walletIdOf(DemoLedgerSeed.KAFE_ID);
        assertThat(accounts.findById(kafeWallet).orElseThrow().getType()).isEqualTo(AccountType.MERCHANT_WALLET);
    }

    private static void checkBalanced(List<PostingLine> lines) {
        LedgerPostingService.checkBalanced(lines);
    }
}
