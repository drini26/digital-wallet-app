package com.drini.wallet.ledger.support;

import com.drini.wallet.ledger.repository.AccountRepository;
import com.drini.wallet.ledger.service.DepositService;
import com.drini.wallet.ledger.service.WalletService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Test helper: creates fresh users with funded wallets (through the real deposit path) and builds the JWT a request
 * of that user would carry.
 */
@Component
public class TestWallets {

    private final WalletService wallets;
    private final DepositService deposits;
    private final AccountRepository accounts;

    public TestWallets(WalletService wallets, DepositService deposits, AccountRepository accounts) {
        this.wallets = wallets;
        this.deposits = deposits;
        this.accounts = accounts;
    }

    public UUID newUser(String accountType, String amount) {
        UUID ownerId = UUID.randomUUID();
        UUID walletId = wallets.createWalletIfMissing(ownerId, accountType, "User " + ownerId.toString().substring(0, 4));
        if (new BigDecimal(amount).signum() > 0) {
            deposits.deposit(walletId, new BigDecimal(amount), "test-" + UUID.randomUUID(), "Test deposit");
        }
        return ownerId;
    }

    public BigDecimal balanceOf(UUID ownerId) {
        UUID walletId = accounts.findWalletIdByOwnerId(ownerId).orElseThrow();
        return accounts.findById(walletId).orElseThrow().getBalance();
    }

    public UUID walletIdOf(UUID ownerId) {
        return accounts.findWalletIdByOwnerId(ownerId).orElseThrow();
    }

    public static RequestPostProcessor customer(UUID userId, String accountType) {
        return jwt()
                .jwt(token -> token.subject(userId.toString())
                        .claim("role", "CUSTOMER")
                        .claim("accountType", accountType)
                        .claim("name", "User " + userId.toString().substring(0, 4)))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    public static RequestPostProcessor manager() {
        return jwt()
                .jwt(token -> token.subject(UUID.randomUUID().toString())
                        .claim("role", "MANAGER")
                        .claim("accountType", "PERSONAL")
                        .claim("name", "Elira Manager"))
                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }
}
