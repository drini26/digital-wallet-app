package com.drini.wallet.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body of POST /api/v1/auth/magic-link: the email or phone that should receive a sign-in link. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MagicLinkRequest {

    @NotBlank
    private String identifier;
}
