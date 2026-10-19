package com.drini.wallet.banking.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Body of POST /internal/holds on the ledger: reserve this amount of this wallet for this transfer. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerHoldRequest {

    private UUID transferId;

    private UUID walletId;

    private BigDecimal amount;

    private LocalDate estimatedArrival;
}
