package com.drini.wallet.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Optional body of POST /api/v1/admin/ledger/reconcile: the business day to reconcile, default today. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReconcileRequest {

    private LocalDate targetDate;
}
