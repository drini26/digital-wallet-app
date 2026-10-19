package com.drini.wallet.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A wallet user (table "users"). */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    private UUID id;

    private String email;

    private String phone;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type")
    private AccountType accountType;

    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    public User() {
    }

    public User(UUID id, Identifier identifier, String displayName, Role role, AccountType accountType) {
        this.id = id;
        if (identifier.isEmail()) {
            this.email = identifier.getValue();
        } else {
            this.phone = identifier.getValue();
        }
        this.displayName = displayName;
        this.role = role;
        this.accountType = accountType;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean owns(Identifier identifier) {
        if (identifier.isEmail()) {
            return identifier.getValue().equals(email);
        }
        return identifier.getValue().equals(phone);
    }
}
