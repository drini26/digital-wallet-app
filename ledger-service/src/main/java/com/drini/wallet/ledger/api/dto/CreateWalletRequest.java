package com.drini.wallet.ledger.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Body of POST /internal/wallets (called by auth-service at sign-up): whose wallet, PERSONAL or MERCHANT. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateWalletRequest {

    @NotNull
    private UUID ownerId;

    @NotNull
    private String accountType;

    private String displayName;
}
