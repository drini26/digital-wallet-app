package com.drini.wallet.banking.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Body of POST /internal/sweeps: move this amount from OPERATING to PARTNER at the bank. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SweepRequest {

    @NotNull
    private UUID sweepId;

    @NotNull
    private BigDecimal amount;
}
