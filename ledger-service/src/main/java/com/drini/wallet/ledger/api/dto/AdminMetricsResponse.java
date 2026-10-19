package com.drini.wallet.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Answer of GET /api/v1/admin/metrics: payment volume, users, errors and speed in the last 24 hours. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminMetricsResponse {

    private int windowHours;

    private BigDecimal totalVolume;

    private long paymentCount;

    private long activeUsers;

    private long rejectedPayments;

    private long systemErrorCount;

    private double averageProcessingMs;

    private long pendingTransfers;

    private long walletCount;

    private boolean outgoingFrozen;
}
