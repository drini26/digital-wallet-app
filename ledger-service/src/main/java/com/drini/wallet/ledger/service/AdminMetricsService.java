package com.drini.wallet.ledger.service;

import com.drini.wallet.common.money.Money;
import com.drini.wallet.ledger.api.dto.AdminMetricsResponse;
import com.drini.wallet.ledger.repository.AccountRepository;
import com.drini.wallet.ledger.repository.PendingTransferRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** The manager's metrics for the last N hours: payment volume and count, active users, errors, speed. */
@Service
public class AdminMetricsService {

    private static final String PAYMENT_STATS_SQL = """
            SELECT COALESCE(SUM(amount), 0)          AS volume,
                   COUNT(*)                          AS payments,
                   COUNT(DISTINCT initiated_by)      AS active_users,
                   COALESCE(AVG(processing_ms), 0)   AS avg_ms
              FROM ledger_transactions
             WHERE type = 'PAYMENT' AND status = 'SUCCESS' AND created_at >= ?
            """;

    private final JdbcTemplate jdbc;
    private final PaymentMetrics paymentMetrics;
    private final PendingTransferRepository pendingTransfers;
    private final AccountRepository accounts;
    private final SystemControlService systemControl;
    private final ErrorEventService errorEvents;

    public AdminMetricsService(JdbcTemplate jdbc, PaymentMetrics paymentMetrics,
                               PendingTransferRepository pendingTransfers, AccountRepository accounts,
                               SystemControlService systemControl, ErrorEventService errorEvents) {
        this.jdbc = jdbc;
        this.paymentMetrics = paymentMetrics;
        this.pendingTransfers = pendingTransfers;
        this.accounts = accounts;
        this.systemControl = systemControl;
        this.errorEvents = errorEvents;
    }

    public AdminMetricsResponse metrics(int windowHours) {
        int hours = Math.max(1, Math.min(windowHours, 24 * 31));
        Instant since = Instant.now().minus(Duration.ofHours(hours));
        Map<String, Object> stats = jdbc.queryForMap(PAYMENT_STATS_SQL, Timestamp.from(since));

        AdminMetricsResponse response = new AdminMetricsResponse();
        response.setWindowHours(hours);
        response.setTotalVolume(Money.normalize((BigDecimal) stats.get("volume")));
        response.setPaymentCount(((Number) stats.get("payments")).longValue());
        response.setActiveUsers(((Number) stats.get("active_users")).longValue());
        response.setAverageProcessingMs(Math.round(((Number) stats.get("avg_ms")).doubleValue() * 10) / 10.0);
        response.setRejectedPayments(paymentMetrics.rejectedCount());
        response.setSystemErrorCount(errorEvents.countServerErrorsSince(since));
        response.setPendingTransfers(pendingTransfers.countByStatus("PENDING"));
        response.setWalletCount(accounts.countWallets());
        response.setOutgoingFrozen(systemControl.isOutgoingFrozen());
        return response;
    }
}
