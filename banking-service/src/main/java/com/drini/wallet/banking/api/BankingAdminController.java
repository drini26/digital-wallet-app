package com.drini.wallet.banking.api;

import com.drini.wallet.banking.api.dto.BatchResultResponse;
import com.drini.wallet.banking.service.BatchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manager endpoint: POST /api/v1/admin/banking/batches/run sends the queued transfers now instead of at 10:00 or
 * 16:00.
 */
@RestController
public class BankingAdminController {

    private final BatchService batchService;

    public BankingAdminController(BatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping("/api/v1/admin/banking/batches/run")
    public BatchResultResponse runBatchNow() {
        return batchService.runBatch("MANUAL");
    }
}
