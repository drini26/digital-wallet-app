package com.drini.wallet.ledger;

import com.drini.wallet.ledger.client.BankingClient;
import com.drini.wallet.ledger.client.dto.BankBalances;
import com.drini.wallet.ledger.domain.SystemAccounts;
import com.drini.wallet.ledger.repository.AccountRepository;
import com.drini.wallet.ledger.service.SystemControlService;
import com.drini.wallet.ledger.support.LedgerTestConfig;
import com.drini.wallet.ledger.support.TestWallets;
import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static com.drini.wallet.ledger.support.TestWallets.customer;
import static com.drini.wallet.ledger.support.TestWallets.manager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The manager's tools: reconciliation with freeze and alert, unfreeze, the sweep, metrics, the error log and payment
 * reversals.
 */
@SpringBootTest(properties = "wallet.jobs.enabled=false")
@AutoConfigureMockMvc
@Import(LedgerTestConfig.class)
class AdminFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestWallets testWallets;
    @Autowired
    private BankingClient banking;
    @Autowired
    private JavaMailSender mailSender;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private SystemControlService systemControl;

    @BeforeEach
    void setUp() {
        Mockito.reset(banking, mailSender);
    }

    @AfterEach
    void alwaysUnfreeze() {
        systemControl.unfreeze(UUID.randomUUID());
    }

    @Test
    void matchingBooksReconcile() throws Exception {
        testWallets.newUser("PERSONAL", "100.00");
        bankSays(ledgerBalance(SystemAccounts.OPERATING_BANK), ledgerBalance(SystemAccounts.PARTNER_RESERVE), 0);

        mockMvc.perform(post("/api/v1/admin/ledger/reconcile").with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECONCILED"))
                .andExpect(jsonPath("$.difference").value(0))
                .andExpect(jsonPath("$.outgoingFrozen").value(false));
    }

    @Test
    void oneCentDifferenceFreezesAndAlerts() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "100.00");
        bankSays(ledgerBalance(SystemAccounts.OPERATING_BANK).add(new BigDecimal("0.01")),
                ledgerBalance(SystemAccounts.PARTNER_RESERVE), 0);

        mockMvc.perform(post("/api/v1/admin/ledger/reconcile").with(manager()))
                .andExpect(jsonPath("$.status").value("MISMATCHED"))
                .andExpect(jsonPath("$.difference").value(0.01))
                .andExpect(jsonPath("$.discrepancies[0]").value(org.hamcrest.Matchers.containsString("Operating account")))
                .andExpect(jsonPath("$.outgoingFrozen").value(true));
        verify(mailSender).send(any(SimpleMailMessage.class));

        mockMvc.perform(post("/internal/holds").header("X-Internal-Api-Key", "dev-internal-key-change-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transferId\":\"" + UUID.randomUUID() + "\",\"walletId\":\"" + testWallets.walletIdOf(ana)
                                + "\",\"amount\":1.00}"))
                .andExpect(jsonPath("$.code").value("SYSTEM_FROZEN"));
        mockMvc.perform(post("/api/v1/admin/banking/sweep").with(manager()))
                .andExpect(jsonPath("$.code").value("SYSTEM_FROZEN"));

        mockMvc.perform(get("/api/v1/admin/system/status").with(manager()))
                .andExpect(jsonPath("$.outgoingFrozen").value(true))
                .andExpect(jsonPath("$.frozenReason").exists());
        mockMvc.perform(post("/api/v1/admin/system/unfreeze").with(manager()))
                .andExpect(jsonPath("$.outgoingFrozen").value(false));
        mockMvc.perform(get("/api/v1/admin/ledger/reconciliations").with(manager()))
                .andExpect(jsonPath("$[0].status").value("MISMATCHED"));
    }

    @Test
    void reconcileForATargetDate() throws Exception {
        testWallets.newUser("PERSONAL", "100.00");
        bankSays(ledgerBalance(SystemAccounts.OPERATING_BANK), ledgerBalance(SystemAccounts.PARTNER_RESERVE), 0);
        String yesterday = LocalDate.now().minusDays(1).toString();

        String body = mockMvc.perform(post("/api/v1/admin/ledger/reconcile").with(manager())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetDate\":\"" + yesterday + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetDate").value(yesterday))
                .andReturn().getResponse().getContentAsString();
        BigDecimal system = new BigDecimal(JsonPath.read(body, "$.totalSystemBalance").toString());
        BigDecimal bank = new BigDecimal(JsonPath.read(body, "$.actualBankBalance").toString());
        assertThat(system).isEqualByComparingTo(bank);

        mockMvc.perform(get("/api/v1/admin/ledger/reconciliations").with(manager()))
                .andExpect(jsonPath("$[0].targetDate").value(yesterday));
        mockMvc.perform(post("/api/v1/admin/ledger/reconcile").with(manager()))
                .andExpect(jsonPath("$.targetDate").value(LocalDate.now().toString()));
        mockMvc.perform(post("/api/v1/admin/ledger/reconcile").with(manager()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetDate\":\"" + LocalDate.now().plusDays(1) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void pendingNotificationsAreReported() throws Exception {
        bankSays(ledgerBalance(SystemAccounts.OPERATING_BANK), ledgerBalance(SystemAccounts.PARTNER_RESERVE), 2);

        mockMvc.perform(post("/api/v1/admin/ledger/reconcile").with(manager()))
                .andExpect(jsonPath("$.discrepancies[0]").value(org.hamcrest.Matchers.containsString("not yet recorded")));
    }

    @Test
    void bankingDownMeansNoReconciliation() throws Exception {
        when(banking.getBankBalances()).thenThrow(new WalletException(ErrorCode.SERVICE_UNAVAILABLE, "down"));

        mockMvc.perform(post("/api/v1/admin/ledger/reconcile").with(manager()))
                .andExpect(status().isServiceUnavailable());
        assertThat(systemControl.isOutgoingFrozen()).isFalse();
    }

    @Test
    void sweepMovesCashAboveTheLimit() throws Exception {
        testWallets.newUser("PERSONAL", "100.00");
        BigDecimal operating = ledgerBalance(SystemAccounts.OPERATING_BANK);
        BigDecimal partner = ledgerBalance(SystemAccounts.PARTNER_RESERVE);
        BigDecimal limit = operating.subtract(new BigDecimal("10.00"));

        mockMvc.perform(post("/api/v1/admin/banking/sweep").with(manager()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reserveLimit\":" + limit.toPlainString() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(10.00));

        verify(banking).sweep(any(), any());
        assertThat(ledgerBalance(SystemAccounts.OPERATING_BANK)).isEqualByComparingTo(operating.subtract(new BigDecimal("10.00")));
        assertThat(ledgerBalance(SystemAccounts.PARTNER_RESERVE)).isEqualByComparingTo(partner.add(new BigDecimal("10.00")));
    }

    @Test
    void sweepOnlyToTheConfiguredPartner() throws Exception {
        mockMvc.perform(post("/api/v1/admin/banking/sweep").with(manager()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reserveLimit\":0,\"destinationPartnerId\":\"OTHER_BANK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(banking, never()).sweep(any(), any());

        testWallets.newUser("PERSONAL", "10.00");
        BigDecimal limit = ledgerBalance(SystemAccounts.OPERATING_BANK).subtract(new BigDecimal("1.00"));
        mockMvc.perform(post("/api/v1/admin/banking/sweep").with(manager()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reserveLimit\":" + limit.toPlainString() + ",\"destinationPartnerId\":\"MOCK_PARTNER_BANK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinationPartnerId").value("MOCK_PARTNER_BANK"))
                .andExpect(jsonPath("$.amount").value(1.00));
    }

    @Test
    void sweepRefusals() throws Exception {
        mockMvc.perform(post("/api/v1/admin/banking/sweep").with(manager()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reserveLimit\":999999999}"))
                .andExpect(jsonPath("$.code").value("NOTHING_TO_SWEEP"));

        testWallets.newUser("PERSONAL", "50.00");
        BigDecimal operating = ledgerBalance(SystemAccounts.OPERATING_BANK);
        doThrow(new WalletException(ErrorCode.SERVICE_UNAVAILABLE, "bank down")).when(banking).sweep(any(), any());
        mockMvc.perform(post("/api/v1/admin/banking/sweep").with(manager()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reserveLimit\":0}"))
                .andExpect(status().isServiceUnavailable());
        assertThat(ledgerBalance(SystemAccounts.OPERATING_BANK)).isEqualByComparingTo(operating);
    }

    @Test
    void managerReversesAPayment() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "100.00");
        UUID besi = testWallets.newUser("PERSONAL", "0");
        String paymentId = pay(ana, besi, "30.00");

        reverse(paymentId).andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(30.00));
        assertThat(testWallets.balanceOf(ana)).isEqualByComparingTo("100.00");
        assertThat(testWallets.balanceOf(besi)).isEqualByComparingTo("0.00");

        reverse(paymentId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_REVERSED"));
        mockMvc.perform(get("/api/v1/admin/transactions").with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("REVERSAL"))
                .andExpect(jsonPath("$[1].reversed").value(true));
    }

    @Test
    void reversalNeedsThePayeeMoney() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "100.00");
        UUID besi = testWallets.newUser("PERSONAL", "0");
        UUID dora = testWallets.newUser("PERSONAL", "0");
        String paymentId = pay(ana, besi, "30.00");
        pay(besi, dora, "20.00");

        reverse(paymentId).andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void reversalRefusals() throws Exception {
        reverse(UUID.randomUUID().toString()).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/admin/transactions/" + UUID.randomUUID() + "/reverse")
                        .with(customer(UUID.randomUUID(), "PERSONAL"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void depositIsNotReversible() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "10.00");
        String history = mockMvc.perform(get("/api/v1/wallet/transactions").with(customer(ana, "PERSONAL")))
                .andReturn().getResponse().getContentAsString();
        String depositId = JsonPath.read(history, "$[0].transactionId");

        reverse(depositId).andExpect(jsonPath("$.code").value("NOT_REVERSIBLE"));
    }

    @Test
    void metricsCountPayments() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "100.00");
        UUID besi = testWallets.newUser("PERSONAL", "0");
        pay(ana, besi, "12.50");
        mockMvc.perform(post("/api/v1/transactions/pay").with(customer(ana, "PERSONAL"))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientId\":\"" + besi + "\",\"amount\":5000,\"currency\":\"EUR\",\"transactionType\":\"P2P\"}"));

        mockMvc.perform(get("/api/v1/admin/metrics").with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowHours").value(24))
                .andExpect(jsonPath("$.paymentCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.activeUsers").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.rejectedPayments").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.outgoingFrozen").value(false));
    }

    @Test
    void serverErrorsAreInTheErrorLog() throws Exception {
        when(banking.getBankBalances()).thenThrow(new WalletException(ErrorCode.SERVICE_UNAVAILABLE, "banking is down"));
        mockMvc.perform(post("/api/v1/admin/ledger/reconcile").with(manager()))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(get("/api/v1/admin/errors").param("size", "5").with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].httpStatus").value(503))
                .andExpect(jsonPath("$[0].code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$[0].service").value("ledger-service"))
                .andExpect(jsonPath("$[0].path").value("/api/v1/admin/ledger/reconcile"));
        mockMvc.perform(get("/api/v1/admin/metrics").with(manager()))
                .andExpect(jsonPath("$.systemErrorCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/v1/admin/errors").param("size", "500").with(manager()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/errors").with(customer(testWallets.newUser("PERSONAL", "0"), "PERSONAL")))
                .andExpect(status().isForbidden());
    }

    private void bankSays(BigDecimal operating, BigDecimal partner, long pendingNotifications) {
        BankBalances balances = new BankBalances();
        balances.setOperatingBalance(operating);
        balances.setPartnerBalance(partner);
        balances.setPendingLedgerNotifications(pendingNotifications);
        when(banking.getBankBalances()).thenReturn(balances);
    }

    private BigDecimal ledgerBalance(UUID accountId) {
        return accounts.findById(accountId).orElseThrow().getBalance();
    }

    private String pay(UUID payer, UUID payee, String amount) throws Exception {
        String body = mockMvc.perform(post("/api/v1/transactions/pay").with(customer(payer, "PERSONAL"))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\":\"" + payee + "\",\"amount\":" + amount
                                + ",\"currency\":\"EUR\",\"transactionType\":\"P2P\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.transactionId");
    }

    private ResultActions reverse(String transactionId) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/transactions/" + transactionId + "/reverse").with(manager())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Customer complaint\"}"));
    }
}
