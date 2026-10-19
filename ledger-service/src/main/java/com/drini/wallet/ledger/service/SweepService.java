package com.drini.wallet.ledger.service;

import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.common.money.Money;
import com.drini.wallet.ledger.api.dto.SweepResponse;
import com.drini.wallet.ledger.client.BankingClient;
import com.drini.wallet.ledger.domain.Account;
import com.drini.wallet.ledger.domain.LedgerTransaction;
import com.drini.wallet.ledger.domain.PostingLine;
import com.drini.wallet.ledger.domain.SweepRun;
import com.drini.wallet.ledger.domain.SystemAccounts;
import com.drini.wallet.ledger.domain.TransactionType;
import com.drini.wallet.ledger.repository.AccountRepository;
import com.drini.wallet.ledger.repository.SweepRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** End-of-day sweep: cash above the reserve limit moves from the operating account to the partner bank. */
@Service
public class SweepService {

    private static final Logger log = LoggerFactory.getLogger(SweepService.class);

    private final AccountRepository accounts;
    private final SweepRunRepository sweepRuns;
    private final LedgerPostingService posting;
    private final SystemControlService systemControl;
    private final BankingClient banking;
    private final TransactionTemplate transaction;
    private final BigDecimal defaultReserveLimit;
    private final String partnerId;

    public SweepService(AccountRepository accounts, SweepRunRepository sweepRuns, LedgerPostingService posting,
                        SystemControlService systemControl, BankingClient banking,
                        PlatformTransactionManager transactionManager,
                        @Value("${wallet.ledger.sweep-reserve-limit}") BigDecimal defaultReserveLimit,
                        @Value("${wallet.ledger.sweep-partner-id}") String partnerId) {
        this.accounts = accounts;
        this.sweepRuns = sweepRuns;
        this.posting = posting;
        this.systemControl = systemControl;
        this.banking = banking;
        this.transaction = new TransactionTemplate(transactionManager);
        this.defaultReserveLimit = defaultReserveLimit;
        this.partnerId = partnerId;
    }

    public SweepResponse sweep(BigDecimal requestedReserveLimit, String destinationPartnerId, UUID requestedBy) {
        BigDecimal reserveLimit = requestedReserveLimit == null ? defaultReserveLimit : requestedReserveLimit;

        if (destinationPartnerId != null && !partnerId.equals(destinationPartnerId)) {
            throw new WalletException(ErrorCode.VALIDATION_FAILED, "Unknown destination partner: " + destinationPartnerId);
        }

        systemControl.requireNotFrozen();

        BigDecimal operating = accounts.findById(SystemAccounts.OPERATING_BANK).orElseThrow().getBalance();
        BigDecimal amount = operating.subtract(reserveLimit);
        if (amount.signum() <= 0) {
            throw new WalletException(ErrorCode.NOTHING_TO_SWEEP, "The operating account (" + operating.toPlainString()
                    + ") is not above the reserve limit (" + reserveLimit.toPlainString() + ")");
        }
        amount = Money.normalize(amount);

        // Bank first, then the ledger. The bank call is idempotent by sweepId, so it is safe to retry.
        UUID sweepId = UUID.randomUUID();
        SweepRun run = new SweepRun();
        run.setId(sweepId);
        run.setAmount(amount);
        run.setReserveLimit(reserveLimit);
        run.setRequestedBy(requestedBy);
        run.setCreatedAt(Instant.now());
        try {
            banking.sweep(sweepId, amount);
        } catch (RuntimeException e) {
            run.setStatus("FAILED");
            run.setFailureReason(e.getMessage());
            sweepRuns.save(run);
            log.warn("Sweep {} failed at the bank: {}", sweepId, e.getMessage());
            throw new WalletException(ErrorCode.SERVICE_UNAVAILABLE, "The bank did not execute the sweep: " + e.getMessage());
        }

        final BigDecimal sweptAmount = amount;
        LedgerTransaction posted = transaction.execute(status -> {
            Map<UUID, Account> locked = posting.lockInIdOrder(
                    List.of(SystemAccounts.OPERATING_BANK, SystemAccounts.PARTNER_RESERVE));
            LedgerTransaction sweepTx = new LedgerTransaction(TransactionType.SWEEP, sweptAmount);
            sweepTx.setExternalReference(sweepId.toString());
            sweepTx.setInitiatedBy(requestedBy);
            sweepTx.setDescription("End-of-day sweep to the partner bank");
            return posting.post(sweepTx, List.of(
                    PostingLine.debit(SystemAccounts.PARTNER_RESERVE, sweptAmount),
                    PostingLine.credit(SystemAccounts.OPERATING_BANK, sweptAmount)), locked);
        });
        run.setStatus("COMPLETED");
        run.setLedgerTransactionId(posted.getId());
        sweepRuns.save(run);
        log.info("Swept {} EUR to the partner bank (reserve limit {})", sweptAmount, reserveLimit);

        SweepResponse response = new SweepResponse();
        response.setSweepId(sweepId);
        response.setStatus("COMPLETED");
        response.setAmount(sweptAmount);
        response.setDestinationPartnerId(partnerId);
        response.setReserveLimit(reserveLimit);
        response.setOperatingBalanceAfter(operating.subtract(sweptAmount));
        response.setTimestamp(run.getCreatedAt());
        return response;
    }
}
