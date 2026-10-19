package com.drini.wallet.ledger.api;

import com.drini.wallet.ledger.api.dto.HoldRequest;
import com.drini.wallet.ledger.api.dto.HoldResponse;
import com.drini.wallet.ledger.api.dto.SystemStatusResponse;
import com.drini.wallet.ledger.api.dto.TransferNotice;
import com.drini.wallet.ledger.service.HoldService;
import com.drini.wallet.ledger.service.SystemControlService;
import com.drini.wallet.ledger.service.TransferSettlementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Endpoints banking-service calls on the ledger. */
@RestController
public class InternalTransferController {

    private final HoldService holdService;
    private final TransferSettlementService settlementService;
    private final SystemControlService systemControl;

    public InternalTransferController(HoldService holdService, TransferSettlementService settlementService,
                                      SystemControlService systemControl) {
        this.holdService = holdService;
        this.settlementService = settlementService;
        this.systemControl = systemControl;
    }

    @PostMapping("/internal/holds")
    public HoldResponse hold(@Valid @RequestBody HoldRequest request) {
        return holdService.hold(request);
    }

    @PostMapping("/internal/transfers/{transferId}/incoming")
    public ResponseEntity<Void> incoming(@PathVariable UUID transferId, @Valid @RequestBody TransferNotice notice) {
        settlementService.incoming(transferId, notice);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/internal/transfers/{transferId}/settled")
    public ResponseEntity<Void> settled(@PathVariable UUID transferId, @Valid @RequestBody TransferNotice notice) {
        settlementService.settled(transferId, notice);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/internal/transfers/{transferId}/failed")
    public ResponseEntity<Void> failed(@PathVariable UUID transferId, @Valid @RequestBody TransferNotice notice) {
        settlementService.failed(transferId, notice);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/internal/system-status")
    public SystemStatusResponse systemStatus() {
        return systemControl.getStatus();
    }
}
