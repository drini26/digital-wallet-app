package com.drini.wallet.banking;

import com.drini.wallet.banking.client.LedgerClient;
import com.drini.wallet.banking.client.dto.LedgerWallet;
import com.drini.wallet.banking.domain.BankTransfer;
import com.drini.wallet.banking.domain.TransferStatus;
import com.drini.wallet.banking.mockbank.MockPartnerBank;
import com.drini.wallet.banking.repository.BankTransferRepository;
import com.drini.wallet.banking.service.BatchService;
import com.drini.wallet.banking.service.LedgerNotifier;
import com.drini.wallet.banking.service.SettlementService;
import com.drini.wallet.banking.support.BankingTestConfig;
import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The whole life of bank transfers against a real database and the mock bank: happy paths and every failure path. */
@SpringBootTest(properties = "wallet.jobs.enabled=false")
@AutoConfigureMockMvc
@Import(BankingTestConfig.class)
class BankTransferFlowTest {

    private static final String VALID_ROUTING = "021000021";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LedgerClient ledger;
    @Autowired
    private BankTransferRepository transfers;
    @Autowired
    private BatchService batchService;
    @Autowired
    private SettlementService settlementService;
    @Autowired
    private LedgerNotifier ledgerNotifier;
    @Autowired
    private MockPartnerBank bank;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;
    private UUID walletId;

    @BeforeEach
    void setUp() {
        Mockito.reset(ledger);
        bank.setMode("NORMAL");
        bank.setSettlementDelaySeconds(0);
        batchService.runBatch("MANUAL");
        settlementService.settleDueTransfers(true);
        userId = UUID.randomUUID();
        walletId = UUID.randomUUID();
        LedgerWallet wallet = new LedgerWallet();
        wallet.setWalletId(walletId);
        wallet.setOwnerId(userId);
        when(ledger.getWallet(walletId)).thenReturn(wallet);
    }

    @Test
    void withdrawalHappyPath() throws Exception {
        BigDecimal bankBefore = bank.getBalance(MockPartnerBank.OPERATING);

        UUID id = createTransfer("OUTFLOW", "40.00", UUID.randomUUID().toString())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.estimatedArrivalDate").exists())
                .andExpect(jsonPath("$.maskedAccountNumber").value("****6789"))
                .andReturn().getResponse().getContentAsString().transform(this::trackingId);
        verify(ledger).hold(any(), any());

        batchService.runBatch("MANUAL");
        assertThat(statusOf(id)).isEqualTo(TransferStatus.SENT);

        settlementService.settleDueTransfers(true);
        BankTransfer settled = transfers.findById(id).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(TransferStatus.SETTLED);
        assertThat(settled.isLedgerNotified()).isTrue();
        verify(ledger).settled(any());
        assertThat(bank.getBalance(MockPartnerBank.OPERATING)).isEqualByComparingTo(bankBefore.subtract(new BigDecimal("40.00")));
    }

    @Test
    void depositHappyPath() throws Exception {
        BigDecimal bankBefore = bank.getBalance(MockPartnerBank.OPERATING);

        UUID id = trackingId(createTransfer("INFLOW", "75.00", UUID.randomUUID().toString())
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        verify(ledger).incoming(any(), any());
        batchService.runBatch("MANUAL");
        settlementService.settleDueTransfers(true);

        assertThat(statusOf(id)).isEqualTo(TransferStatus.SETTLED);
        assertThat(bank.getBalance(MockPartnerBank.OPERATING)).isEqualByComparingTo(bankBefore.add(new BigDecimal("75.00")));
    }

    @Test
    void bankOfflineThenRetry() throws Exception {
        UUID id = trackingId(createTransfer("OUTFLOW", "10.00", UUID.randomUUID().toString())
                .andReturn().getResponse().getContentAsString());
        bank.setMode("OFFLINE");

        batchService.runBatch("MANUAL");
        BankTransfer waiting = transfers.findById(id).orElseThrow();
        assertThat(waiting.getStatus()).isEqualTo(TransferStatus.PENDING_RETRY);
        assertThat(waiting.getNextAttemptAt()).isNotNull();
        assertThat(waiting.getLastError()).contains("offline");

        bank.setMode("NORMAL");
        jdbc.update("UPDATE bank_transfers SET next_attempt_at = now() - interval '1 second' WHERE id = ?", id);
        batchService.retryDue();

        BankTransfer sent = transfers.findById(id).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(TransferStatus.SENT);
        assertThat(sent.getAttemptCount()).isEqualTo(2);
        assertThat(receivedAtBank(id)).isEqualTo(1);
    }

    @Test
    void lostAnswerDoesNotChargeTwice() throws Exception {
        BigDecimal bankBefore = bank.getBalance(MockPartnerBank.OPERATING);
        UUID id = trackingId(createTransfer("OUTFLOW", "25.00", UUID.randomUUID().toString())
                .andReturn().getResponse().getContentAsString());
        bank.setMode("ACK_LOST");

        batchService.runBatch("MANUAL");
        assertThat(statusOf(id)).isEqualTo(TransferStatus.PENDING_RETRY);
        jdbc.update("UPDATE bank_transfers SET next_attempt_at = now() - interval '1 second' WHERE id = ?", id);
        batchService.retryDue();
        settlementService.settleDueTransfers(true);

        assertThat(statusOf(id)).isEqualTo(TransferStatus.SETTLED);
        assertThat(receivedAtBank(id)).isEqualTo(1);
        assertThat(bank.getBalance(MockPartnerBank.OPERATING)).isEqualByComparingTo(bankBefore.subtract(new BigDecimal("25.00")));
    }

    @Test
    void rejectedWithdrawalReturnsTheMoney() throws Exception {
        UUID id = trackingId(createTransfer("OUTFLOW", "30.00", UUID.randomUUID().toString())
                .andReturn().getResponse().getContentAsString());
        bank.setMode("REJECT_NEXT");

        batchService.runBatch("MANUAL");

        BankTransfer rejected = transfers.findById(id).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(TransferStatus.REJECTED);
        assertThat(rejected.isLedgerNotified()).isTrue();
        verify(ledger).failed(any());
        assertThat(bank.getMode()).isEqualTo("NORMAL");
    }

    @Test
    void ledgerDownAtSettlementIsRetried() throws Exception {
        UUID id = trackingId(createTransfer("OUTFLOW", "5.00", UUID.randomUUID().toString())
                .andReturn().getResponse().getContentAsString());
        batchService.runBatch("MANUAL");
        doThrow(new WalletException(ErrorCode.SERVICE_UNAVAILABLE, "ledger down")).when(ledger).settled(any());

        settlementService.settleDueTransfers(true);
        assertThat(transfers.findById(id).orElseThrow().isLedgerNotified()).isFalse();
        assertThat(ledgerNotifier.countPendingNotifications()).isGreaterThanOrEqualTo(1);

        Mockito.reset(ledger);
        ledgerNotifier.retryPendingNotifications();
        assertThat(transfers.findById(id).orElseThrow().isLedgerNotified()).isTrue();
    }

    @Test
    void frozenBatchSkipsWithdrawals() throws Exception {
        UUID withdrawal = trackingId(createTransfer("OUTFLOW", "1.00", UUID.randomUUID().toString())
                .andReturn().getResponse().getContentAsString());
        UUID deposit = trackingId(createTransfer("INFLOW", "1.00", UUID.randomUUID().toString())
                .andReturn().getResponse().getContentAsString());
        when(ledger.isOutgoingFrozen()).thenReturn(true);

        mockMvc.perform(post("/api/v1/admin/banking/batches/run").with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skippedFrozen").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        assertThat(statusOf(withdrawal)).isEqualTo(TransferStatus.QUEUED);
        assertThat(statusOf(deposit)).isEqualTo(TransferStatus.SENT);
    }

    @Test
    void insufficientFundsFailsTheTransfer() throws Exception {
        doThrow(new WalletException(ErrorCode.INSUFFICIENT_FUNDS, "not enough")).when(ledger).hold(any(), any());

        createTransfer("OUTFLOW", "9999.00", UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(transfers.findTop50ByOwnerIdOrderByCreatedAtDesc(userId).get(0).getStatus())
                .isEqualTo(TransferStatus.FAILED);
    }

    @Test
    void ledgerDownAtCreationCanBeRetriedWithTheSameKey() throws Exception {
        String key = UUID.randomUUID().toString();
        doThrow(new WalletException(ErrorCode.SERVICE_UNAVAILABLE, "down")).when(ledger).hold(any(), any());
        createTransfer("OUTFLOW", "3.00", key).andExpect(status().isServiceUnavailable());
        assertThat(transfers.findByOwnerIdAndIdempotencyKey(userId, key).orElseThrow().getStatus())
                .isEqualTo(TransferStatus.REQUESTED);

        Mockito.doNothing().when(ledger).hold(any(), any());
        createTransfer("OUTFLOW", "3.00", key).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("QUEUED"));
        createTransfer("OUTFLOW", "3.00", key).andExpect(status().isAccepted());
        assertThat(transfers.findTop50ByOwnerIdOrderByCreatedAtDesc(userId)).hasSize(1);
    }

    @Test
    void someoneElsesWalletIsRefused() throws Exception {
        UUID otherWallet = UUID.randomUUID();
        LedgerWallet wallet = new LedgerWallet();
        wallet.setWalletId(otherWallet);
        wallet.setOwnerId(UUID.randomUUID());
        when(ledger.getWallet(otherWallet)).thenReturn(wallet);

        mockMvc.perform(post("/api/v1/bank/transfer").with(customer()).header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("OUTFLOW", "1.00", VALID_ROUTING).replace(walletId.toString(), otherWallet.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
        verify(ledger, never()).hold(any(), any());
    }

    @Test
    void invalidInputIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/bank/transfer").with(customer()).header("Idempotency-Key", "k2")
                        .contentType(MediaType.APPLICATION_JSON).content(body("OUTFLOW", "1.00", "021000022")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ROUTING_NUMBER"));
        mockMvc.perform(post("/api/v1/bank/transfer").with(customer()).header("Idempotency-Key", "k3")
                        .contentType(MediaType.APPLICATION_JSON).content(body("OUTFLOW", "1.00", VALID_ROUTING).replace("EUR", "USD")))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
        mockMvc.perform(post("/api/v1/bank/transfer").with(customer()).header("Idempotency-Key", "k4")
                        .contentType(MediaType.APPLICATION_JSON).content(body("OUTFLOW", "1.00", "12345")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userSeesTheirTransfers() throws Exception {
        createTransfer("OUTFLOW", "2.00", UUID.randomUUID().toString());
        createTransfer("INFLOW", "3.00", UUID.randomUUID().toString());

        mockMvc.perform(get("/api/v1/bank/transfers").with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].transferDirection").value("INFLOW"));
    }

    @Test
    void mockBankControls() throws Exception {
        BigDecimal before = bank.getBalance(MockPartnerBank.OPERATING);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/mock-bank/settings")
                        .with(manager()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"OFFLINE\",\"settlementDelaySeconds\":7}"))
                .andExpect(jsonPath("$.mode").value("OFFLINE"))
                .andExpect(jsonPath("$.settlementDelaySeconds").value(7));
        mockMvc.perform(post("/api/v1/admin/mock-bank/inject-discrepancy").with(manager()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/mock-bank/settle-now").with(manager()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/mock-bank/settings").with(customer()))
                .andExpect(status().isForbidden());

        assertThat(bank.getBalance(MockPartnerBank.OPERATING)).isEqualByComparingTo(before.add(new BigDecimal("0.01")));
    }

    @Test
    void internalBalancesAndSweep() throws Exception {
        bank.injectDiscrepancy(new BigDecimal("100.00"));
        BigDecimal operatingBefore = bank.getBalance(MockPartnerBank.OPERATING);
        BigDecimal partnerBefore = bank.getBalance(MockPartnerBank.PARTNER);
        String sweep = "{\"sweepId\":\"" + UUID.randomUUID() + "\",\"amount\":60.00}";

        mockMvc.perform(post("/internal/sweeps").header("X-Internal-Api-Key", "dev-internal-key-change-me")
                .contentType(MediaType.APPLICATION_JSON).content(sweep)).andExpect(status().isNoContent());
        mockMvc.perform(post("/internal/sweeps").header("X-Internal-Api-Key", "dev-internal-key-change-me")
                .contentType(MediaType.APPLICATION_JSON).content(sweep)).andExpect(status().isNoContent());

        assertThat(bank.getBalance(MockPartnerBank.OPERATING)).isEqualByComparingTo(operatingBefore.subtract(new BigDecimal("60.00")));
        assertThat(bank.getBalance(MockPartnerBank.PARTNER)).isEqualByComparingTo(partnerBefore.add(new BigDecimal("60.00")));
        mockMvc.perform(get("/internal/bank-balances").header("X-Internal-Api-Key", "dev-internal-key-change-me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatingBalance").exists())
                .andExpect(jsonPath("$.pendingLedgerNotifications").exists());

        bank.setMode("OFFLINE");
        mockMvc.perform(post("/internal/sweeps").header("X-Internal-Api-Key", "dev-internal-key-change-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sweepId\":\"" + UUID.randomUUID() + "\",\"amount\":1.00}"))
                .andExpect(status().isServiceUnavailable());
    }

    private ResultActions createTransfer(String direction, String amount, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/bank/transfer").with(customer()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body(direction, amount, VALID_ROUTING)));
    }

    private String body(String direction, String amount, String routing) {
        return "{\"sourceWalletId\":\"" + walletId + "\",\"bankRoutingNumber\":\"" + routing
                + "\",\"externalAccountNumber\":\"123456789\",\"transferDirection\":\"" + direction
                + "\",\"amount\":" + amount + ",\"currency\":\"EUR\"}";
    }

    private UUID trackingId(String json) {
        return UUID.fromString(JsonPath.read(json, "$.trackingId"));
    }

    private TransferStatus statusOf(UUID transferId) {
        return transfers.findById(transferId).orElseThrow().getStatus();
    }

    private int receivedAtBank(UUID transferId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mock_bank_received WHERE bank_reference = ?", Integer.class,
                transferId.toString());
    }

    private RequestPostProcessor customer() {
        return jwt().jwt(token -> token.subject(userId.toString()).claim("role", "CUSTOMER"))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static RequestPostProcessor manager() {
        return jwt().jwt(token -> token.subject(UUID.randomUUID().toString()).claim("role", "MANAGER"))
                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }
}
