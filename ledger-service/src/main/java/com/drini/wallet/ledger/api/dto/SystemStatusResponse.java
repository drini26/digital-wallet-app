package com.drini.wallet.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Whether outgoing money is frozen after a reconciliation mismatch, and why. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatusResponse {

    private boolean outgoingFrozen;

    private String frozenReason;

    private Instant frozenAt;

    private Instant unfrozenAt;
}
