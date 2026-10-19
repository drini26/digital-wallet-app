package com.drini.wallet.auth.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Answer of a successful login: the JWT, when it expires, and who the user is (so the UI can pick its pages). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    private String tokenType = "Bearer";
    private Instant expiresAt;
    private UUID userId;
    private String displayName;
    private String role;
    private String accountType;
}
