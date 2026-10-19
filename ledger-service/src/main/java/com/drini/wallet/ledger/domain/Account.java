package com.drini.wallet.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A ledger account (table "accounts"): a user's wallet or one of the company's system accounts. */
@Entity
@Table(name = "accounts")
@Getter
@Setter
public class Account {

    @Id
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    private AccountType type;

    @Column(name = "display_name")
    private String displayName;

    private String currency;

    private BigDecimal balance;

    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    public Account() {
    }

    /** Assets grow with a DEBIT, liabilities (user wallets) grow with a CREDIT. */
    public void applyEntry(Direction direction, BigDecimal amount) {
        boolean increases = (direction == Direction.DEBIT) == type.isAsset();
        if (increases) {
            balance = balance.add(amount);
        } else {
            balance = balance.subtract(amount);
        }
    }
}
