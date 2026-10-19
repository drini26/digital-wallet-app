package com.drini.wallet.banking.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** A wallet as the ledger describes it (answer of GET /internal/wallets/{id}): we check ownerId. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerWallet {

    private UUID walletId;

    private UUID ownerId;

    private String accountType;

    private BigDecimal balance;
}
