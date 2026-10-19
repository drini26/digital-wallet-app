package com.drini.wallet.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Answer of a sweep: how much moved to the partner bank and what stays in the operating account. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SweepResponse {

    private UUID sweepId;

    private String status;

    private BigDecimal amount;

    private String destinationPartnerId;

    private BigDecimal reserveLimit;

    private BigDecimal operatingBalanceAfter;

    private Instant timestamp;
}
