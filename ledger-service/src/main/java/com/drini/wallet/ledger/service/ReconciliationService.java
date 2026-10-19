package com.drini.wallet.ledger.service;

import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.common.money.Money;
import com.drini.wallet.ledger.api.dto.ReconciliationResponse;
import com.drini.wallet.ledger.client.BankingClient;
import com.drini.wallet.ledger.client.dto.BankBalances;
import com.drini.wallet.ledger.domain.ReconciliationRun;
import com.drini.wallet.ledger.domain.SystemAccounts;
import com.drini.wallet.ledger.repository.AccountRepository;
import com.drini.wallet.ledger.repository.ReconciliationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Reconciliation: the books must balance internally and match the bank to the cent; any difference freezes outgoing
 * money.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final AccountRepository accounts;
    private final ReconciliationRunRepository runs;
    private final BankingClient banking;
    private final SystemControlService systemControl;
    private final AlertService alerts;

    public ReconciliationService(AccountRepository accounts, ReconciliationRunRepository runs, BankingClient banking,
                                 SystemControlService systemControl, AlertService alerts) {
        this.accounts = accounts;
        this.runs = runs;
        this.banking = banking;
        this.systemControl = systemControl;
        this.alerts = alerts;
    }

    public ReconciliationResponse reconcile(LocalDate targetDate, String triggeredBy) {

        LocalDate today = LocalDate.now();
        LocalDate businessDay = targetDate == null ? today : targetDate;
        if (businessDay.isAfter(today)) {
            throw new WalletException(ErrorCode.VALIDATION_FAILED, "targetDate cannot be in the future");
        }

        // If banking is down we cannot compare, so nothing is frozen.
        BankBalances bank = banking.getBankBalances();

        BigDecimal wallets = Money.normalize(accounts.sumOfAllWallets());
        BigDecimal outgoingPending = balanceOf(SystemAccounts.OUTGOING_PENDING);
        BigDecimal ledgerOperating = balanceOf(SystemAccounts.OPERATING_BANK);
        BigDecimal ledgerPartner = balanceOf(SystemAccounts.PARTNER_RESERVE);

        List<String> discrepancies = new ArrayList<>();
        BigDecimal owed = wallets.add(outgoingPending);
        BigDecimal held = ledgerOperating.add(ledgerPartner);
        BigDecimal internalDifference = owed.subtract(held);
        if (internalDifference.signum() != 0) {
            discrepancies.add("Internal: wallets + pending withdrawals = " + owed.toPlainString()
                    + " but operating + partner = " + held.toPlainString());
        }
        BigDecimal operatingDifference = ledgerOperating.subtract(bank.getOperatingBalance());
        if (operatingDifference.signum() != 0) {
            discrepancies.add("Operating account: ledger " + ledgerOperating.toPlainString() + ", bank "
                    + bank.getOperatingBalance().toPlainString() + " (difference " + operatingDifference.toPlainString() + ")");
        }
        BigDecimal partnerDifference = ledgerPartner.subtract(bank.getPartnerBalance());
        if (partnerDifference.signum() != 0) {
            discrepancies.add("Partner reserve: ledger " + ledgerPartner.toPlainString() + ", bank "
                    + bank.getPartnerBalance().toPlainString() + " (difference " + partnerDifference.toPlainString() + ")");
        }
        if (bank.getPendingLedgerNotifications() > 0) {
            discrepancies.add(bank.getPendingLedgerNotifications()
                    + " bank result(s) not yet recorded in the ledger (banking is retrying)");
        }
        BigDecimal totalDifference = internalDifference.abs().add(operatingDifference.abs()).add(partnerDifference.abs());
        boolean reconciled = totalDifference.signum() == 0;

        ReconciliationRun run = new ReconciliationRun();
        run.setId(UUID.randomUUID());
        run.setStatus(reconciled ? "RECONCILED" : "MISMATCHED");
        run.setWalletsTotal(wallets);
        run.setLedgerOperating(ledgerOperating);
        run.setLedgerPartner(ledgerPartner);
        run.setBankOperating(bank.getOperatingBalance());
        run.setBankPartner(bank.getPartnerBalance());
        run.setDifference(totalDifference);
        run.setDiscrepancies(String.join("\n", discrepancies));
        run.setTriggeredBy(triggeredBy);
        run.setCreatedAt(Instant.now());
        run.setTargetDate(businessDay);
        run.setOutgoingPending(outgoingPending);
        runs.save(run);

        // Zero-variance rule: any difference freezes outgoing money and alerts the team.
        if (!reconciled) {
            String details = String.join("\n", discrepancies);
            systemControl.freeze("Reconciliation " + run.getId() + " found a difference of "
                    + totalDifference.toPlainString() + " EUR");
            log.error("RECONCILIATION MISMATCH ({} EUR): {}", totalDifference.toPlainString(), details);
            alerts.sendFreezeAlert(details);
        } else {
            log.info("Reconciliation OK: books match the bank to the cent");
        }
        return toResponse(run, systemControl.isOutgoingFrozen());
    }

    public List<ReconciliationResponse> recentRuns() {
        List<ReconciliationResponse> responses = new ArrayList<>();
        for (ReconciliationRun run : runs.findTop10ByOrderByCreatedAtDesc()) {
            responses.add(toResponse(run, false));
        }
        return responses;
    }

    private BigDecimal balanceOf(UUID accountId) {
        return accounts.findById(accountId).orElseThrow().getBalance();
    }

    private static ReconciliationResponse toResponse(ReconciliationRun run, boolean frozen) {
        ReconciliationResponse response = new ReconciliationResponse();
        response.setId(run.getId());
        response.setStatus(run.getStatus());
        response.setTargetDate(run.getTargetDate());
        response.setWalletsTotal(run.getWalletsTotal());
        response.setOutgoingPending(run.getOutgoingPending());
        if (run.getOutgoingPending() != null) {
            response.setTotalSystemBalance(run.getWalletsTotal().add(run.getOutgoingPending()));
        }
        if (run.getBankOperating() != null && run.getBankPartner() != null) {
            response.setActualBankBalance(run.getBankOperating().add(run.getBankPartner()));
        }
        response.setLedgerOperating(run.getLedgerOperating());
        response.setLedgerPartner(run.getLedgerPartner());
        response.setBankOperating(run.getBankOperating());
        response.setBankPartner(run.getBankPartner());
        response.setDifference(run.getDifference());
        List<String> discrepancies = new ArrayList<>();
        if (run.getDiscrepancies() != null && !run.getDiscrepancies().isEmpty()) {
            discrepancies.addAll(Arrays.asList(run.getDiscrepancies().split("\n")));
        }
        response.setDiscrepancies(discrepancies);
        response.setOutgoingFrozen(frozen);
        response.setCreatedAt(run.getCreatedAt());
        return response;
    }
}
