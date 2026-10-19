package com.drini.wallet.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** A wallet as seen by other services (internal API): its id, owner, type and balance. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {

    private UUID walletId;

    private UUID ownerId;

    private String accountType;

    private BigDecimal balance;
}
