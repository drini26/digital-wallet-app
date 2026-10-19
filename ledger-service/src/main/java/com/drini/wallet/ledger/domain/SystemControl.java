package com.drini.wallet.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** The single row (id = 1) of table "system_control": is outgoing money frozen? */
@Entity
@Table(name = "system_control")
@Getter
@Setter
public class SystemControl {

    public static final short ID = 1;

    @Id
    private Short id;

    @Column(name = "outgoing_frozen")
    private boolean outgoingFrozen;

    @Column(name = "frozen_reason")
    private String frozenReason;

    @Column(name = "frozen_at")
    private Instant frozenAt;

    @Column(name = "unfrozen_at")
    private Instant unfrozenAt;

    @Column(name = "unfrozen_by")
    private UUID unfrozenBy;

    public SystemControl() {
    }
}
