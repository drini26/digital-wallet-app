package com.drini.wallet.banking.mockbank;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Demo data: the OPERATING account at the mock bank starts with 1,500.00, the total of the ledger's opening deposits.
 */
@Component
@Profile("demo")
public class MockBankSeed implements ApplicationRunner {

    public static final BigDecimal OPENING_BALANCE = new BigDecimal("1500.00");

    private final MockPartnerBank bank;

    public MockBankSeed(MockPartnerBank bank) {
        this.bank = bank;
    }

    @Override
    public void run(ApplicationArguments args) {
        bank.seedOpeningBalance(OPENING_BALANCE);
    }
}
