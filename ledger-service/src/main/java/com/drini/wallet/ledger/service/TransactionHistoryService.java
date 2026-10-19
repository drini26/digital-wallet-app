package com.drini.wallet.ledger.service;

import com.drini.wallet.ledger.api.dto.TransactionItemResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The wallet history (GET /api/v1/wallet/transactions): every entry on the user's wallet, newest first. */
@Service
public class TransactionHistoryService {

    private static final String HISTORY_SQL = """
            SELECT t.id, t.type, t.payment_type, e.direction, e.amount, t.currency, t.description, t.created_at,
                   CASE WHEN e.direction = 'DEBIT' THEN payee.display_name ELSE payer.display_name END AS counterparty
              FROM ledger_entries e
              JOIN ledger_transactions t ON t.id = e.transaction_id
              LEFT JOIN accounts payer ON payer.id = t.payer_account_id
              LEFT JOIN accounts payee ON payee.id = t.payee_account_id
             WHERE e.account_id = ?
             ORDER BY e.id DESC
             LIMIT ?
            """;

    private final JdbcTemplate jdbc;
    private final WalletService wallets;

    public TransactionHistoryService(JdbcTemplate jdbc, WalletService wallets) {
        this.jdbc = jdbc;
        this.wallets = wallets;
    }

    @Transactional
    public List<TransactionItemResponse> history(UUID ownerId, String userAccountType, String displayName, int limit) {
        UUID walletId = wallets.createWalletIfMissing(ownerId, userAccountType, displayName);
        int safeLimit = Math.max(1, Math.min(limit, 200));

        List<TransactionItemResponse> items = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(HISTORY_SQL, walletId, safeLimit)) {
            TransactionItemResponse item = new TransactionItemResponse();
            item.setTransactionId((UUID) row.get("id"));
            item.setType((String) row.get("type"));
            item.setPaymentType((String) row.get("payment_type"));
            item.setDirection("CREDIT".equals(row.get("direction")) ? "IN" : "OUT");
            item.setAmount((BigDecimal) row.get("amount"));
            item.setCurrency((String) row.get("currency"));
            item.setDescription((String) row.get("description"));
            item.setCounterpartyName((String) row.get("counterparty"));
            item.setCreatedAt(((Timestamp) row.get("created_at")).toInstant());
            items.add(item);
        }
        return items;
    }
}
