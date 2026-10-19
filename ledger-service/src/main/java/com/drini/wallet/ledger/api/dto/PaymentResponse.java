package com.drini.wallet.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Answer of a payment: the transaction id, status, time, and the payer's balance right after it. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private UUID transactionId;

    private String status;

    private Instant timestamp;

    private String transactionType;

    private UUID recipientId;

    private BigDecimal amount;

    private String currency;

    private BigDecimal availableBalance;

    private boolean replayed;
}
