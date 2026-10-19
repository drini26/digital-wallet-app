package com.drini.wallet.auth.api.dto;

import com.drini.wallet.auth.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body of POST /api/v1/auth/register: the new user's name, email or phone, and PERSONAL or MERCHANT. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank
    private String displayName;

    @NotBlank
    private String identifier;

    @NotNull
    private AccountType accountType;
}
