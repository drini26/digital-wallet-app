package com.drini.wallet.banking.mockbank;

import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A fake partner bank for the demo. */
@Component
public class MockPartnerBank {

    public static final String OPERATING = "OPERATING";
    public static final String PARTNER = "PARTNER";

    private static final Logger log = LoggerFactory.getLogger(MockPartnerBank.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public MockPartnerBank(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public String submit(String bankReference, String direction, BigDecimal amount) {
        String mode = getMode();
        if ("OFFLINE".equals(mode)) {
            throw new BankUnavailableException("The partner bank is offline");
        }

        String result = transaction.execute(status -> receive(bankReference, direction, amount, mode));

        if ("ACK_LOST".equals(mode)) {
            setMode("NORMAL");
            log.info("Mock bank received {} but the answer is lost (ACK_LOST)", bankReference);
            throw new BankUnavailableException("No answer from the partner bank (answer lost)");
        }
        return result;
    }

    private String receive(String bankReference, String direction, BigDecimal amount, String mode) {
        List<String> existing = jdbc.queryForList(
                "SELECT status FROM mock_bank_received WHERE bank_reference = ?", String.class, bankReference);
        if (!existing.isEmpty()) {
            log.info("Mock bank already received {}: answering {} again", bankReference, existing.get(0));
            return existing.get(0);
        }

        String status = "ACCEPTED";
        if ("REJECT_NEXT".equals(mode)) {
            status = "REJECTED";
            jdbc.update("UPDATE mock_bank_settings SET mode = 'NORMAL' WHERE id = 1");
        }
        Integer delaySeconds = jdbc.queryForObject(
                "SELECT settlement_delay_seconds FROM mock_bank_settings WHERE id = 1", Integer.class);
        Instant settleAfter = Instant.now().plusSeconds(delaySeconds);
        jdbc.update("INSERT INTO mock_bank_received (bank_reference, direction, amount, status, settle_after) "
                + "VALUES (?, ?, ?, ?, ?)", bankReference, direction, amount, status, Timestamp.from(settleAfter));
        return status;
    }

    public List<String> settleDue(boolean ignoreDelay) {
        String sql = "SELECT bank_reference, direction, amount FROM mock_bank_received WHERE status = 'ACCEPTED'";
        if (!ignoreDelay) {
            sql = sql + " AND settle_after <= now()";
        }
        List<String> settled = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(sql)) {
            String reference = (String) row.get("bank_reference");
            BigDecimal amount = (BigDecimal) row.get("amount");
            BigDecimal change = "OUTFLOW".equals(row.get("direction")) ? amount.negate() : amount;
            transaction.executeWithoutResult(status -> {
                moveMoney(OPERATING, change, "SETTLEMENT", reference);
                jdbc.update("UPDATE mock_bank_received SET status = 'SETTLED', settled_at = now() "
                        + "WHERE bank_reference = ?", reference);
            });
            settled.add(reference);
        }
        return settled;
    }

    public void sweep(UUID sweepId, BigDecimal amount) {
        if ("OFFLINE".equals(getMode())) {
            throw new BankUnavailableException("The partner bank is offline");
        }
        String reference = "sweep-" + sweepId;
        transaction.executeWithoutResult(status -> {
            Integer done = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM mock_bank_movements WHERE reference = ?", Integer.class, reference);
            if (done != null && done > 0) {
                return;
            }
            if (getBalance(OPERATING).compareTo(amount) < 0) {
                throw new WalletException(ErrorCode.INSUFFICIENT_FUNDS, "Not enough money in the operating account");
            }
            moveMoney(OPERATING, amount.negate(), "SWEEP", reference);
            moveMoney(PARTNER, amount, "SWEEP", reference);
        });
    }

    public void injectDiscrepancy(BigDecimal amount) {
        transaction.executeWithoutResult(status ->
                moveMoney(OPERATING, amount, "INJECTED_DISCREPANCY", "inject-" + UUID.randomUUID()));
        log.warn("Mock bank: injected a discrepancy of {} on the OPERATING account", amount);
    }

    public void seedOpeningBalance(BigDecimal amount) {
        transaction.executeWithoutResult(status -> {
            Integer done = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM mock_bank_movements WHERE reference = 'seed-opening'", Integer.class);
            if (done != null && done == 0) {
                moveMoney(OPERATING, amount, "SEED", "seed-opening");
            }
        });
    }

    public BigDecimal getBalance(String accountCode) {
        return jdbc.queryForObject("SELECT balance FROM mock_bank_accounts WHERE code = ?", BigDecimal.class,
                accountCode);
    }

    public String getMode() {
        return jdbc.queryForObject("SELECT mode FROM mock_bank_settings WHERE id = 1", String.class);
    }

    public int getSettlementDelaySeconds() {
        return jdbc.queryForObject("SELECT settlement_delay_seconds FROM mock_bank_settings WHERE id = 1", Integer.class);
    }

    public void setMode(String mode) {
        jdbc.update("UPDATE mock_bank_settings SET mode = ? WHERE id = 1", mode);
    }

    public void setSettlementDelaySeconds(int seconds) {
        jdbc.update("UPDATE mock_bank_settings SET settlement_delay_seconds = ? WHERE id = 1", seconds);
    }

    private void moveMoney(String accountCode, BigDecimal change, String reason, String reference) {
        jdbc.update("INSERT INTO mock_bank_movements (account_code, amount, reason, reference) VALUES (?, ?, ?, ?)",
                accountCode, change, reason, reference);
        jdbc.update("UPDATE mock_bank_accounts SET balance = balance + ? WHERE code = ?", change, accountCode);
    }
}
