package com.drini.wallet.ledger.service;

import com.drini.wallet.ledger.api.dto.AdminTransactionResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The manager's list of recent payments (GET /api/v1/admin/transactions), with both names and whether each one was
 * already reversed.
 */
@Service
public class AdminTransactionService {

    private static final String RECENT_PAYMENTS_SQL = """
            SELECT t.id, t.type, t.payment_type, t.amount, t.description, t.created_at,
                   payer.display_name AS payer_name, payee.display_name AS payee_name,
                   EXISTS (SELECT 1 FROM ledger_transactions r WHERE r.reversal_of = t.id) AS reversed
              FROM ledger_transactions t
              LEFT JOIN accounts payer ON payer.id = t.payer_account_id
              LEFT JOIN accounts payee ON payee.id = t.payee_account_id
             WHERE t.type IN ('PAYMENT', 'REVERSAL')
             ORDER BY t.created_at DESC
             LIMIT ?
            """;

    private final JdbcTemplate jdbc;

    public AdminTransactionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AdminTransactionResponse> recentPayments(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<AdminTransactionResponse> items = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(RECENT_PAYMENTS_SQL, safeLimit)) {
            AdminTransactionResponse item = new AdminTransactionResponse();
            item.setTransactionId((UUID) row.get("id"));
            item.setType((String) row.get("type"));
            item.setPaymentType((String) row.get("payment_type"));
            item.setAmount((BigDecimal) row.get("amount"));
            item.setDescription((String) row.get("description"));
            item.setPayerName((String) row.get("payer_name"));
            item.setPayeeName((String) row.get("payee_name"));
            item.setReversed(Boolean.TRUE.equals(row.get("reversed")));
            item.setCreatedAt(((Timestamp) row.get("created_at")).toInstant());
            items.add(item);
        }
        return items;
    }
}
