package com.drini.wallet.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Answer of a reversal: the new REVERSAL transaction and the payment it undoes. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReversalResponse {

    private UUID reversalTransactionId;

    private UUID originalTransactionId;

    private BigDecimal amount;

    private Instant timestamp;
}
