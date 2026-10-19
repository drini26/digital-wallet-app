package com.drini.wallet.banking.api;

import com.drini.wallet.banking.api.dto.CreateTransferRequest;
import com.drini.wallet.banking.api.dto.TransferResponse;
import com.drini.wallet.banking.service.TransferService;
import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Customer endpoints for bank transfers. */
@RestController
@RequestMapping("/api/v1/bank")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransferResponse createTransfer(@AuthenticationPrincipal Jwt jwt,
                                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                                           @Valid @RequestBody CreateTransferRequest request) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new WalletException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key must be 1 to 100 characters");
        }
        return transferService.createTransfer(CurrentUser.from(jwt).getUserId(), request, idempotencyKey);
    }

    @GetMapping("/transfers")
    public List<TransferResponse> myTransfers(@AuthenticationPrincipal Jwt jwt) {
        return transferService.listTransfers(CurrentUser.from(jwt).getUserId());
    }
}
