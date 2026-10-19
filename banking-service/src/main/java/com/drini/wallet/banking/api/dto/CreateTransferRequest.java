package com.drini.wallet.banking.api.dto;

import com.drini.wallet.banking.domain.TransferDirection;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Body of POST /api/v1/bank/transfer: from which wallet, to or from which bank account, how much. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransferRequest {

    @NotNull
    private UUID sourceWalletId;

    @NotNull
    @Pattern(regexp = "^[0-9]{9}$", message = "must be 9 digits")
    private String bankRoutingNumber;

    @NotNull
    @Pattern(regexp = "^[0-9]{4,17}$", message = "must be 4 to 17 digits")
    private String externalAccountNumber;

    @NotNull
    private TransferDirection transferDirection;

    @NotNull
    private BigDecimal amount;

    @NotNull
    private String currency;
}
