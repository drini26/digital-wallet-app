package com.drini.wallet.ledger.api;

import com.drini.wallet.common.security.CurrentUser;
import com.drini.wallet.ledger.api.dto.AdminMetricsResponse;
import com.drini.wallet.ledger.api.dto.AdminTransactionResponse;
import com.drini.wallet.ledger.api.dto.ErrorEventResponse;
import com.drini.wallet.ledger.api.dto.ReconcileRequest;
import com.drini.wallet.ledger.api.dto.ReconciliationResponse;
import com.drini.wallet.ledger.api.dto.ReversalResponse;
import com.drini.wallet.ledger.api.dto.ReverseRequest;
import com.drini.wallet.ledger.api.dto.SweepRequest;
import com.drini.wallet.ledger.api.dto.SweepResponse;
import com.drini.wallet.ledger.api.dto.SystemStatusResponse;
import com.drini.wallet.ledger.service.AdminMetricsService;
import com.drini.wallet.ledger.service.AdminTransactionService;
import com.drini.wallet.ledger.service.ErrorEventService;
import com.drini.wallet.ledger.service.ReconciliationService;
import com.drini.wallet.ledger.service.ReversalService;
import com.drini.wallet.ledger.service.SweepService;
import com.drini.wallet.ledger.service.SystemControlService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Manager endpoints of the ledger (MANAGER role, checked by the gateway and again by SecurityConfig). */
@RestController
public class AdminController {

    private final SweepService sweepService;
    private final ReconciliationService reconciliationService;
    private final AdminMetricsService metricsService;
    private final SystemControlService systemControl;
    private final AdminTransactionService transactionService;
    private final ReversalService reversalService;
    private final ErrorEventService errorEvents;

    public AdminController(SweepService sweepService, ReconciliationService reconciliationService,
                           AdminMetricsService metricsService, SystemControlService systemControl,
                           AdminTransactionService transactionService, ReversalService reversalService,
                           ErrorEventService errorEvents) {
        this.sweepService = sweepService;
        this.reconciliationService = reconciliationService;
        this.metricsService = metricsService;
        this.systemControl = systemControl;
        this.transactionService = transactionService;
        this.reversalService = reversalService;
        this.errorEvents = errorEvents;
    }

    @PostMapping("/api/v1/admin/banking/sweep")
    public SweepResponse sweep(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) SweepRequest request) {
        if (request == null) {
            return sweepService.sweep(null, null, CurrentUser.from(jwt).getUserId());
        }
        return sweepService.sweep(request.getReserveLimit(), request.getDestinationPartnerId(),
                CurrentUser.from(jwt).getUserId());
    }

    @PostMapping("/api/v1/admin/ledger/reconcile")
    public ReconciliationResponse reconcile(@AuthenticationPrincipal Jwt jwt,
                                            @RequestBody(required = false) ReconcileRequest request) {
        return reconciliationService.reconcile(request == null ? null : request.getTargetDate(),
                "MANAGER " + CurrentUser.from(jwt).getUserId());
    }

    @GetMapping("/api/v1/admin/ledger/reconciliations")
    public List<ReconciliationResponse> reconciliations() {
        return reconciliationService.recentRuns();
    }

    @GetMapping("/api/v1/admin/metrics")
    public AdminMetricsResponse metrics(@RequestParam(defaultValue = "24") int hours) {
        return metricsService.metrics(hours);
    }

    @GetMapping("/api/v1/admin/errors")
    public List<ErrorEventResponse> errors(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return errorEvents.recent(page, size);
    }

    @GetMapping("/api/v1/admin/system/status")
    public SystemStatusResponse systemStatus() {
        return systemControl.getStatus();
    }

    @PostMapping("/api/v1/admin/system/unfreeze")
    public SystemStatusResponse unfreeze(@AuthenticationPrincipal Jwt jwt) {
        return systemControl.unfreeze(CurrentUser.from(jwt).getUserId());
    }

    @GetMapping("/api/v1/admin/transactions")
    public List<AdminTransactionResponse> transactions(@RequestParam(defaultValue = "50") int limit) {
        return transactionService.recentPayments(limit);
    }

    @PostMapping("/api/v1/admin/transactions/{transactionId}/reverse")
    public ReversalResponse reverse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID transactionId,
                                    @Valid @RequestBody ReverseRequest request) {
        return reversalService.reverse(transactionId, CurrentUser.from(jwt).getUserId(), request.getReason());
    }
}
