package com.drini.wallet.ledger.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Body of the /internal/transfers/{id}/incoming, /settled and /failed calls: which wallet, direction and amount. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferNotice {

    @NotNull
    private String direction;

    @NotNull
    private UUID walletId;

    @NotNull
    private BigDecimal amount;

    private LocalDate estimatedArrival;
}
