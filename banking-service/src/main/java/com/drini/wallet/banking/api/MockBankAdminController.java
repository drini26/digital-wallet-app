package com.drini.wallet.banking.api;

import com.drini.wallet.banking.api.dto.DiscrepancyRequest;
import com.drini.wallet.banking.api.dto.MockBankSettingsRequest;
import com.drini.wallet.banking.api.dto.MockBankStatusResponse;
import com.drini.wallet.banking.mockbank.MockPartnerBank;
import com.drini.wallet.banking.service.SettlementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** Demo controls of the mock bank (manager only), used on the "Mock bank" admin page. */
@RestController
@RequestMapping("/api/v1/admin/mock-bank")
public class MockBankAdminController {

    private final MockPartnerBank bank;
    private final SettlementService settlementService;

    public MockBankAdminController(MockPartnerBank bank, SettlementService settlementService) {
        this.bank = bank;
        this.settlementService = settlementService;
    }

    @GetMapping("/settings")
    public MockBankStatusResponse status() {
        MockBankStatusResponse response = new MockBankStatusResponse();
        response.setMode(bank.getMode());
        response.setSettlementDelaySeconds(bank.getSettlementDelaySeconds());
        response.setOperatingBalance(bank.getBalance(MockPartnerBank.OPERATING));
        response.setPartnerBalance(bank.getBalance(MockPartnerBank.PARTNER));
        return response;
    }

    @PutMapping("/settings")
    public MockBankStatusResponse updateSettings(@Valid @RequestBody MockBankSettingsRequest request) {
        bank.setMode(request.getMode());
        if (request.getSettlementDelaySeconds() != null) {
            bank.setSettlementDelaySeconds(request.getSettlementDelaySeconds());
        }
        return status();
    }

    @PostMapping("/settle-now")
    public MockBankStatusResponse settleNow() {
        settlementService.settleDueTransfers(true);
        return status();
    }

    @PostMapping("/inject-discrepancy")
    public MockBankStatusResponse injectDiscrepancy(@RequestBody(required = false) DiscrepancyRequest request) {
        BigDecimal amount = new BigDecimal("0.01");
        if (request != null && request.getAmount() != null) {
            amount = request.getAmount();
        }
        bank.injectDiscrepancy(amount);
        return status();
    }
}
