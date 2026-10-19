package com.drini.wallet.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Answer of GET /api/v1/wallet/balance: available money plus transfers still on their way. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponse {

    private UUID walletId;

    private String accountType;

    private String currency;

    private BigDecimal availableBalance;

    private BigDecimal pendingIncoming;

    private BigDecimal pendingOutgoing;

    private BigDecimal pendingBalance;
}
