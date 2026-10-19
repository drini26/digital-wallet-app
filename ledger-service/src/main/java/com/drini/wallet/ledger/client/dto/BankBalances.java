package com.drini.wallet.ledger.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Answer of banking GET /internal/bank-balances: the bank balances, and how many results the ledger has not heard
 * yet.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BankBalances {

    private BigDecimal operatingBalance;

    private BigDecimal partnerBalance;

    private long pendingLedgerNotifications;
}
