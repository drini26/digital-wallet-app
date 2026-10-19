package com.drini.wallet.ledger.domain;

import java.util.UUID;

/** The fixed ids of the company's own accounts, created by the V1__ledger.sql migration. */
public final class SystemAccounts {

    public static final UUID OPERATING_BANK = UUID.fromString("00000000-0000-7000-9000-000000000001");
    public static final UUID PARTNER_RESERVE = UUID.fromString("00000000-0000-7000-9000-000000000002");
    public static final UUID OUTGOING_PENDING = UUID.fromString("00000000-0000-7000-9000-000000000003");

    private SystemAccounts() {
    }
}
