package com.drini.wallet.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Answer of POST /api/v1/admin/ledger/reconcile: our books vs each other and vs the bank. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationResponse {

    private UUID id;

    private String status;

    private BigDecimal walletsTotal;

    private BigDecimal outgoingPending;

    private BigDecimal ledgerOperating;

    private BigDecimal ledgerPartner;

    private BigDecimal bankOperating;

    private BigDecimal bankPartner;

    private BigDecimal difference;

    private List<String> discrepancies;

    private boolean outgoingFrozen;

    private Instant createdAt;

    private LocalDate targetDate;

    private BigDecimal totalSystemBalance;

    private BigDecimal actualBankBalance;
}
