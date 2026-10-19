package com.drini.wallet.ledger.api.dto;

import com.drini.wallet.ledger.domain.PaymentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Body of POST /api/v1/transactions/pay: who gets the money, how much, and P2P or MERCHANT. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PayRequest {

    @NotNull
    private UUID recipientId;

    @NotNull
    private BigDecimal amount;

    @NotNull
    private String currency;

    @NotNull
    private PaymentType transactionType;

    @Size(max = 200)
    private String description;
}
