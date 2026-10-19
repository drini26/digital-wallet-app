package com.drini.wallet.ledger.job;

import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.ledger.service.ReconciliationService;
import com.drini.wallet.ledger.service.SweepService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The ledger's nightly jobs: the end-of-day sweep at 23:00, then the reconciliation at 23:30. */
@Component
@ConditionalOnProperty(name = "wallet.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class LedgerJobs {

    private static final Logger log = LoggerFactory.getLogger(LedgerJobs.class);

    private final SweepService sweepService;
    private final ReconciliationService reconciliationService;

    public LedgerJobs(SweepService sweepService, ReconciliationService reconciliationService) {
        this.sweepService = sweepService;
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(cron = "${wallet.ledger.sweep-cron}")
    public void endOfDaySweep() {
        try {
            sweepService.sweep(null, null, null);
        } catch (WalletException e) {
            log.info("End-of-day sweep skipped: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${wallet.ledger.reconciliation-cron}")
    public void nightlyReconciliation() {
        try {
            reconciliationService.reconcile(null, "SCHEDULED");
        } catch (WalletException e) {
            log.error("Nightly reconciliation could not run: {}", e.getMessage());
        }
    }
}
