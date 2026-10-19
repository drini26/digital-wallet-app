package com.drini.wallet.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One payment in the manager's transaction list, with both names and whether it was already reversed. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminTransactionResponse {

    private UUID transactionId;

    private String type;

    private String paymentType;

    private BigDecimal amount;

    private String payerName;

    private String payeeName;

    private String description;

    private boolean reversed;

    private Instant createdAt;
}
