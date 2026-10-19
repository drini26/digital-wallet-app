package com.drini.wallet.ledger.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Body of banking POST /internal/sweeps: move this amount from OPERATING to PARTNER at the bank. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BankSweepRequest {

    private UUID sweepId;

    private BigDecimal amount;
}
