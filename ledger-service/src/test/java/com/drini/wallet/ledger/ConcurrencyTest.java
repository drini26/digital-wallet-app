package com.drini.wallet.ledger;

import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.common.security.CurrentUser;
import com.drini.wallet.ledger.api.dto.PayRequest;
import com.drini.wallet.ledger.domain.PaymentType;
import com.drini.wallet.ledger.service.PaymentService;
import com.drini.wallet.ledger.support.LedgerTestConfig;
import com.drini.wallet.ledger.support.TestWallets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Many payments from one wallet at the same moment: no overdraft, no lost update. */
@SpringBootTest(properties = "wallet.jobs.enabled=false")
@Import(LedgerTestConfig.class)
class ConcurrencyTest {

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private TestWallets testWallets;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void parallelPaymentsNeverOverdraw() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "1000.00");
        UUID besi = testWallets.newUser("PERSONAL", "0");
        CurrentUser anaUser = new CurrentUser(ana, "CUSTOMER", "PERSONAL", "Ana");

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(24);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    paymentService.pay(anaUser, payment(besi, "10.00"), UUID.randomUUID().toString());
                    succeeded.incrementAndGet();
                } catch (WalletException e) {
                    if (e.getErrorCode() == ErrorCode.INSUFFICIENT_FUNDS) {
                        insufficient.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(succeeded.get()).isEqualTo(100);
        assertThat(insufficient.get()).isEqualTo(20);
        assertThat(testWallets.balanceOf(ana)).isEqualByComparingTo("0.00");
        assertThat(testWallets.balanceOf(besi)).isEqualByComparingTo("1000.00");
        assertLedgerBalanced();
    }

    @Test
    void oppositePaymentsDoNotDeadlock() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "500.00");
        UUID besi = testWallets.newUser("PERSONAL", "500.00");
        CurrentUser anaUser = new CurrentUser(ana, "CUSTOMER", "PERSONAL", "Ana");
        CurrentUser besiUser = new CurrentUser(besi, "CUSTOMER", "PERSONAL", "Besi");

        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futures.add(pool.submit(() -> paymentService.pay(anaUser, payment(besi, "1.00"), UUID.randomUUID().toString())));
            futures.add(pool.submit(() -> paymentService.pay(besiUser, payment(ana, "2.00"), UUID.randomUUID().toString())));
        }
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(testWallets.balanceOf(ana)).isEqualByComparingTo("550.00");
        assertThat(testWallets.balanceOf(besi)).isEqualByComparingTo("450.00");
        assertLedgerBalanced();
    }

    private void assertLedgerBalanced() {
        BigDecimal debits = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM ledger_entries WHERE direction = 'DEBIT'", BigDecimal.class);
        BigDecimal credits = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM ledger_entries WHERE direction = 'CREDIT'", BigDecimal.class);
        assertThat(debits).isEqualByComparingTo(credits);

        Integer walletsWithWrongBalance = jdbc.queryForObject("""
                SELECT COUNT(*) FROM accounts a
                 WHERE a.owner_id IS NOT NULL
                   AND a.balance <> (SELECT COALESCE(SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
                                       FROM ledger_entries e WHERE e.account_id = a.id)
                """, Integer.class);
        assertThat(walletsWithWrongBalance).isZero();
    }

    private static PayRequest payment(UUID recipient, String amount) {
        PayRequest request = new PayRequest();
        request.setRecipientId(recipient);
        request.setAmount(new BigDecimal(amount));
        request.setCurrency("EUR");
        request.setTransactionType(PaymentType.P2P);
        return request;
    }
}
