package com.drini.wallet.auth.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Answer of GET /api/v1/users/lookup: who a payment would go to (id, name, PERSONAL or MERCHANT). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserLookupResponse {

    private UUID userId;
    private String displayName;
    private String accountType;
}
