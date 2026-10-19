package com.drini.wallet.banking.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Body of POST /api/v1/admin/mock-bank/inject-discrepancy: how much to add to the bank balance (default 0.01). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiscrepancyRequest {

    private BigDecimal amount;
}
