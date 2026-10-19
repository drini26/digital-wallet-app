package com.drini.wallet.ledger.api;

import com.drini.wallet.ledger.api.dto.CreateWalletRequest;
import com.drini.wallet.ledger.api.dto.WalletResponse;
import com.drini.wallet.ledger.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Wallet endpoints for the other services (protected by the X-Internal-Api-Key header, never routed by the gateway).
 */
@RestController
@RequestMapping("/internal/wallets")
public class InternalWalletController {

    private final WalletService walletService;

    public InternalWalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    public WalletResponse createWallet(@Valid @RequestBody CreateWalletRequest request) {
        walletService.createWalletIfMissing(request.getOwnerId(), request.getAccountType(), request.getDisplayName());
        return walletService.getWalletByOwner(request.getOwnerId());
    }

    @GetMapping("/{walletId}")
    public WalletResponse wallet(@PathVariable UUID walletId) {
        return walletService.getWallet(walletId);
    }
}
