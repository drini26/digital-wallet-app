package com.drini.wallet.ledger.api;

import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.common.security.CurrentUser;
import com.drini.wallet.ledger.api.dto.PayRequest;
import com.drini.wallet.ledger.api.dto.PaymentResponse;
import com.drini.wallet.ledger.service.PaymentMetrics;
import com.drini.wallet.ledger.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST /api/v1/transactions/pay: pay another user or a merchant. */
@RestController
@RequestMapping("/api/v1/transactions")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentMetrics metrics;

    public PaymentController(PaymentService paymentService, PaymentMetrics metrics) {
        this.paymentService = paymentService;
        this.metrics = metrics;
    }

    @PostMapping("/pay")
    public ResponseEntity<PaymentResponse> pay(@AuthenticationPrincipal Jwt jwt,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @Valid @RequestBody PayRequest request) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new WalletException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key must be 1 to 100 characters");
        }
        long startedAt = System.nanoTime();
        try {
            PaymentResponse response = paymentService.pay(CurrentUser.from(jwt), request, idempotencyKey);
            metrics.recordDuration(System.nanoTime() - startedAt);
            if (response.isReplayed()) {
                return ResponseEntity.ok().header("Idempotent-Replay", "true").body(response);
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (WalletException e) {
            metrics.recordRejected();
            throw e;
        }
    }
}
