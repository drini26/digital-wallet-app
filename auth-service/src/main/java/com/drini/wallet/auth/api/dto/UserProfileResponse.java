package com.drini.wallet.auth.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Answer of GET /api/v1/users/me: the signed-in user's own profile. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private UUID id;
    private String displayName;
    private String email;
    private String phone;
    private String role;
    private String accountType;
}
