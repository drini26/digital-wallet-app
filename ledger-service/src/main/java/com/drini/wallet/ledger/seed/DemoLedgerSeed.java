package com.drini.wallet.ledger.seed;

import com.drini.wallet.ledger.service.DepositService;
import com.drini.wallet.ledger.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Demo data: opening balances for Ana, Besi and Kafe Tirana, posted as normal double-entry deposits. */
@Component
@Profile("demo")
public class DemoLedgerSeed implements ApplicationRunner {

    public static final UUID ANA_ID = UUID.fromString("00000000-0000-7000-8000-000000000002");
    public static final UUID BESI_ID = UUID.fromString("00000000-0000-7000-8000-000000000003");
    public static final UUID KAFE_ID = UUID.fromString("00000000-0000-7000-8000-000000000004");

    private static final Logger log = LoggerFactory.getLogger(DemoLedgerSeed.class);

    private final WalletService wallets;
    private final DepositService deposits;

    public DemoLedgerSeed(WalletService wallets, DepositService deposits) {
        this.wallets = wallets;
        this.deposits = deposits;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedWallet(ANA_ID, "PERSONAL", "Ana Customer", new BigDecimal("1000.00"));
        seedWallet(BESI_ID, "PERSONAL", "Besi Customer", new BigDecimal("500.00"));
        seedWallet(KAFE_ID, "MERCHANT", "Kafe Tirana", BigDecimal.ZERO);
        log.info("Demo wallets ready: Ana 1000.00, Besi 500.00, Kafe Tirana 0.00");
    }

    private void seedWallet(UUID ownerId, String accountType, String displayName, BigDecimal openingBalance) {
        UUID walletId = wallets.createWalletIfMissing(ownerId, accountType, displayName);
        if (openingBalance.signum() > 0) {
            deposits.deposit(walletId, openingBalance, "seed-" + ownerId, "Opening balance");
        }
    }
}
