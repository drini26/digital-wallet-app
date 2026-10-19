package com.drini.wallet.auth.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Body of POST /internal/wallets on the ledger: whose wallet to create, and of which type. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateWalletRequest {

    private UUID ownerId;
    private String accountType;
    private String displayName;
}
