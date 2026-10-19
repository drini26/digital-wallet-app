package com.drini.wallet.common.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** The signed-in user, read from the already verified JWT. */
public class CurrentUser {

    private final UUID userId;
    private final String role;
    private final String accountType;
    private final String name;

    public CurrentUser(UUID userId, String role, String accountType, String name) {
        this.userId = userId;
        this.role = role;
        this.accountType = accountType;
        this.name = name;
    }

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString(WalletJwt.CLAIM_ROLE),
                jwt.getClaimAsString(WalletJwt.CLAIM_ACCOUNT_TYPE),
                jwt.getClaimAsString(WalletJwt.CLAIM_NAME));
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getName() {
        return name;
    }

    public boolean isManager() {
        return WalletJwt.ROLE_MANAGER.equals(role);
    }
}
