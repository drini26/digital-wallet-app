package com.drini.wallet.ledger.service;

import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.ledger.api.dto.SystemStatusResponse;
import com.drini.wallet.ledger.domain.SystemControl;
import com.drini.wallet.ledger.repository.SystemControlRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** The freeze switch for outgoing money. */
@Service
public class SystemControlService {

    private static final Logger log = LoggerFactory.getLogger(SystemControlService.class);

    private final SystemControlRepository repository;

    public SystemControlService(SystemControlRepository repository, MeterRegistry registry) {
        this.repository = repository;
        Gauge.builder("wallet.system.frozen", repository, repo -> repo.findById(SystemControl.ID).orElseThrow()
                        .isOutgoingFrozen() ? 1 : 0)
                .description("1 while outgoing money is frozen after a reconciliation mismatch")
                .register(registry);
    }

    @Transactional(readOnly = true)
    public boolean isOutgoingFrozen() {
        return load().isOutgoingFrozen();
    }

    @Transactional(readOnly = true)
    public void requireNotFrozen() {
        SystemControl control = load();
        if (control.isOutgoingFrozen()) {
            throw new WalletException(ErrorCode.SYSTEM_FROZEN,
                    "Outgoing money is frozen after a reconciliation mismatch. A manager must unfreeze it.");
        }
    }

    @Transactional(readOnly = true)
    public SystemStatusResponse getStatus() {
        SystemControl control = load();
        SystemStatusResponse response = new SystemStatusResponse();
        response.setOutgoingFrozen(control.isOutgoingFrozen());
        response.setFrozenReason(control.getFrozenReason());
        response.setFrozenAt(control.getFrozenAt());
        response.setUnfrozenAt(control.getUnfrozenAt());
        return response;
    }

    @Transactional
    public void freeze(String reason) {
        SystemControl control = load();
        control.setOutgoingFrozen(true);
        control.setFrozenReason(reason);
        control.setFrozenAt(Instant.now());
        log.error("OUTGOING MONEY FROZEN: {}", reason);
    }

    @Transactional
    public SystemStatusResponse unfreeze(UUID managerId) {
        SystemControl control = load();
        control.setOutgoingFrozen(false);
        control.setUnfrozenAt(Instant.now());
        control.setUnfrozenBy(managerId);
        log.warn("Outgoing money unfrozen by manager {}", managerId);
        return getStatus();
    }

    private SystemControl load() {
        return repository.findById(SystemControl.ID).orElseThrow();
    }
}
