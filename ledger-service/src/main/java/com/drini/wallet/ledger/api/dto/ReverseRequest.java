package com.drini.wallet.ledger.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body of POST /api/v1/admin/transactions/{id}/reverse: why the manager reverses the payment. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReverseRequest {

    @NotBlank
    @Size(max = 200)
    private String reason;
}
