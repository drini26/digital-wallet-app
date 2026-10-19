package com.drini.wallet.banking.job;

import com.drini.wallet.banking.service.BatchService;
import com.drini.wallet.banking.service.LedgerNotifier;
import com.drini.wallet.banking.service.SettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** All scheduled jobs of banking-service in one place. */
@Component
@ConditionalOnProperty(name = "wallet.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class BankingJobs {

    private static final Logger log = LoggerFactory.getLogger(BankingJobs.class);

    private final BatchService batchService;
    private final SettlementService settlementService;
    private final LedgerNotifier ledgerNotifier;

    public BankingJobs(BatchService batchService, SettlementService settlementService, LedgerNotifier ledgerNotifier) {
        this.batchService = batchService;
        this.settlementService = settlementService;
        this.ledgerNotifier = ledgerNotifier;
    }

    @Scheduled(cron = "${wallet.banking.batch-cron}")
    public void runScheduledBatch() {
        batchService.runBatch("SCHEDULED");
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    public void retryDueTransfers() {
        int sent = batchService.retryDue();
        if (sent > 0) {
            log.info("Retry job: {} transfer(s) reached the bank", sent);
        }
    }

    @Scheduled(fixedDelay = 5_000, initialDelay = 5_000)
    public void settleDueTransfers() {
        settlementService.settleDueTransfers(false);
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 10_000)
    public void retryLedgerNotifications() {
        ledgerNotifier.retryPendingNotifications();
    }
}
