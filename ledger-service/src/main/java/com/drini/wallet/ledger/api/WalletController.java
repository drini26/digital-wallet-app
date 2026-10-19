package com.drini.wallet.ledger.api;

import com.drini.wallet.common.security.CurrentUser;
import com.drini.wallet.ledger.api.dto.BalanceResponse;
import com.drini.wallet.ledger.api.dto.TransactionItemResponse;
import com.drini.wallet.ledger.service.TransactionHistoryService;
import com.drini.wallet.ledger.service.WalletService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The signed-in user's wallet. */
@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final WalletService walletService;
    private final TransactionHistoryService historyService;

    public WalletController(WalletService walletService, TransactionHistoryService historyService) {
        this.walletService = walletService;
        this.historyService = historyService;
    }

    @GetMapping("/balance")
    public BalanceResponse balance(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        return walletService.getBalance(user.getUserId(), user.getAccountType(), user.getName());
    }

    @GetMapping("/transactions")
    public List<TransactionItemResponse> transactions(@AuthenticationPrincipal Jwt jwt,
                                                      @RequestParam(defaultValue = "50") int limit) {
        CurrentUser user = CurrentUser.from(jwt);
        return historyService.history(user.getUserId(), user.getAccountType(), user.getName(), limit);
    }
}
