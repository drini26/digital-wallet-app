package com.drini.wallet.ledger.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Body of POST /internal/holds (banking -> ledger): reserve money in a wallet for a withdrawal. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HoldRequest {

    @NotNull
    private UUID transferId;

    @NotNull
    private UUID walletId;

    @NotNull
    private BigDecimal amount;

    private LocalDate estimatedArrival;
}
