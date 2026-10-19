package com.drini.wallet.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Body of POST /api/v1/admin/banking/sweep: keep this much in the operating account, sweep the rest. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SweepRequest {

    private BigDecimal reserveLimit;

    private String destinationPartnerId;
}
