package com.drini.wallet.banking.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** The mock bank's state for the admin page: mode, delay, and our two account balances. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MockBankStatusResponse {

    private String mode;

    private int settlementDelaySeconds;

    private BigDecimal operatingBalance;

    private BigDecimal partnerBalance;
}
