package com.drini.wallet.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One line of the wallet history: money IN or OUT of my wallet, with the other party or a description. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionItemResponse {

    private UUID transactionId;

    private String type;

    private String paymentType;

    private String direction;

    private BigDecimal amount;

    private String currency;

    private String counterpartyName;

    private String description;

    private Instant createdAt;
}
