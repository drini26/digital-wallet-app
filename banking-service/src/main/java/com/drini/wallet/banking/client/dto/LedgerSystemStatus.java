package com.drini.wallet.banking.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Answer of GET /internal/system-status on the ledger: is outgoing money frozen? */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerSystemStatus {

    private boolean outgoingFrozen;

    private String frozenReason;
}
