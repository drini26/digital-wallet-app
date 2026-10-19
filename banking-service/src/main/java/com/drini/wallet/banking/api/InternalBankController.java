package com.drini.wallet.banking.api;

import com.drini.wallet.banking.api.dto.BankBalancesResponse;
import com.drini.wallet.banking.api.dto.SweepRequest;
import com.drini.wallet.banking.mockbank.BankUnavailableException;
import com.drini.wallet.banking.mockbank.MockPartnerBank;
import com.drini.wallet.banking.service.LedgerNotifier;
import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.common.money.Money;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints the ledger calls on banking (X-Internal-Api-Key required, never routed by the gateway). */
@RestController
public class InternalBankController {

    private final MockPartnerBank bank;
    private final LedgerNotifier ledgerNotifier;

    public InternalBankController(MockPartnerBank bank, LedgerNotifier ledgerNotifier) {
        this.bank = bank;
        this.ledgerNotifier = ledgerNotifier;
    }

    @GetMapping("/internal/bank-balances")
    public BankBalancesResponse balances() {
        BankBalancesResponse response = new BankBalancesResponse();
        response.setOperatingBalance(bank.getBalance(MockPartnerBank.OPERATING));
        response.setPartnerBalance(bank.getBalance(MockPartnerBank.PARTNER));
        response.setPendingLedgerNotifications(ledgerNotifier.countPendingNotifications());
        return response;
    }

    @PostMapping("/internal/sweeps")
    public ResponseEntity<Void> sweep(@Valid @RequestBody SweepRequest request) {
        try {
            bank.sweep(request.getSweepId(), Money.requirePositiveAmount(request.getAmount()));
        } catch (BankUnavailableException e) {
            throw new WalletException(ErrorCode.SERVICE_UNAVAILABLE, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }
}
