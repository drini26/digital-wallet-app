package com.drini.wallet.banking.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A bank transfer as the user sees it (answer of POST /bank/transfer and GET /bank/transfers). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {

    private UUID trackingId;

    private String transferDirection;

    private BigDecimal amount;

    private String currency;

    private String status;

    private LocalDate estimatedArrivalDate;

    private String maskedAccountNumber;

    private int attemptCount;

    private String lastError;

    private Instant createdAt;
}
