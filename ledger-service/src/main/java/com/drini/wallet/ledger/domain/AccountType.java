package com.drini.wallet.ledger.domain;

/** The kinds of ledger accounts (see V1__ledger.sql). */
public enum AccountType {
    PERSONAL_WALLET,
    MERCHANT_WALLET,
    OPERATING_BANK,
    PARTNER_RESERVE,
    OUTGOING_PENDING;

    public boolean isAsset() {
        return this == OPERATING_BANK || this == PARTNER_RESERVE;
    }

    public boolean isWallet() {
        return this == PERSONAL_WALLET || this == MERCHANT_WALLET;
    }

    public static AccountType walletFor(String userAccountType) {
        if ("MERCHANT".equals(userAccountType)) {
            return MERCHANT_WALLET;
        }
        return PERSONAL_WALLET;
    }
}
