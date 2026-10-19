package com.drini.wallet.banking.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Answer of GET /internal/bank-balances: what the bank says we have, for reconciliation. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BankBalancesResponse {

    private BigDecimal operatingBalance;

    private BigDecimal partnerBalance;

    private long pendingLedgerNotifications;
}
