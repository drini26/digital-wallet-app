package com.drini.wallet.banking.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Body of /internal/transfers/{id}/incoming, /settled and /failed on the ledger. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerTransferNotice {

    private String direction;

    private UUID walletId;

    private BigDecimal amount;

    private LocalDate estimatedArrival;
}
