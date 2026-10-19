package com.drini.wallet.ledger;

import com.drini.wallet.ledger.support.LedgerTestConfig;
import com.drini.wallet.ledger.support.TestWallets;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static com.drini.wallet.ledger.support.TestWallets.customer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Payments through the HTTP API with a real PostgreSQL: business rules, idempotent replays, balance and history.
 */
@SpringBootTest(properties = "wallet.jobs.enabled=false")
@AutoConfigureMockMvc
@Import(LedgerTestConfig.class)
class PaymentFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestWallets testWallets;

    @Test
    void anaPaysBesi() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "100.00");
        UUID besi = testWallets.newUser("PERSONAL", "0");

        pay(ana, "PERSONAL", besi, "30.00", "P2P", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.availableBalance").value(70.00))
                .andExpect(jsonPath("$.transactionId").exists());

        assertThat(testWallets.balanceOf(ana)).isEqualByComparingTo("70.00");
        assertThat(testWallets.balanceOf(besi)).isEqualByComparingTo("30.00");
    }

    @Test
    void anaCannotPayMoreThanSheHas() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "20.00");
        UUID besi = testWallets.newUser("PERSONAL", "0");

        pay(ana, "PERSONAL", besi, "20.01", "P2P", UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(testWallets.balanceOf(ana)).isEqualByComparingTo("20.00");
        assertThat(testWallets.balanceOf(besi)).isEqualByComparingTo("0.00");
    }

    @Test
    void anaCannotPayHerself() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "50.00");

        pay(ana, "PERSONAL", ana, "5.00", "P2P", UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELF_PAYMENT_NOT_ALLOWED"));
    }

    @Test
    void paymentTypeMustMatchTheRecipient() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "50.00");
        UUID besi = testWallets.newUser("PERSONAL", "0");
        UUID kafe = testWallets.newUser("MERCHANT", "0");

        pay(ana, "PERSONAL", besi, "5.00", "MERCHANT", UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RECIPIENT_TYPE_MISMATCH"));
        pay(ana, "PERSONAL", kafe, "5.00", "P2P", UUID.randomUUID().toString())
                .andExpect(jsonPath("$.code").value("RECIPIENT_TYPE_MISMATCH"));
        pay(ana, "PERSONAL", kafe, "5.00", "MERCHANT", UUID.randomUUID().toString())
                .andExpect(status().isCreated());
    }

    @Test
    void sameIdempotencyKeyChargesOnlyOnce() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "100.00");
        UUID besi = testWallets.newUser("PERSONAL", "0");
        String key = UUID.randomUUID().toString();

        String first = pay(ana, "PERSONAL", besi, "10.00", "P2P", key)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstTransactionId = JsonPath.read(first, "$.transactionId");
        pay(ana, "PERSONAL", besi, "10.00", "P2P", key)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.transactionId").value(firstTransactionId))
                .andExpect(jsonPath("$.availableBalance").value(90.00));

        assertThat(testWallets.balanceOf(ana)).isEqualByComparingTo("90.00");
    }

    @Test
    void reusedKeyForAnotherPaymentIsRefused() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "100.00");
        UUID besi = testWallets.newUser("PERSONAL", "0");
        String key = UUID.randomUUID().toString();
        pay(ana, "PERSONAL", besi, "10.00", "P2P", key).andExpect(status().isCreated());

        pay(ana, "PERSONAL", besi, "11.00", "P2P", key)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(testWallets.balanceOf(ana)).isEqualByComparingTo("90.00");
    }

    @Test
    void otherInvalidPaymentsAreRefused() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "100.00");
        UUID besi = testWallets.newUser("PERSONAL", "0");

        pay(ana, "PERSONAL", UUID.randomUUID(), "1.00", "P2P", UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPIENT_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/transactions/pay").with(customer(ana, "PERSONAL"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(besi, "1.00", "P2P").replace("EUR", "USD")))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
        pay(ana, "PERSONAL", besi, "1.001", "P2P", UUID.randomUUID().toString())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        pay(ana, "PERSONAL", besi, "10000.01", "P2P", UUID.randomUUID().toString())
                .andExpect(jsonPath("$.code").value("AMOUNT_LIMIT_EXCEEDED"));
        mockMvc.perform(post("/api/v1/transactions/pay").with(customer(ana, "PERSONAL"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(besi, "1.00", "P2P")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        assertThat(testWallets.balanceOf(ana)).isEqualByComparingTo("100.00");
    }

    @Test
    void balanceAndHistoryShowThePayment() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "100.00");
        UUID besi = testWallets.newUser("PERSONAL", "0");
        pay(ana, "PERSONAL", besi, "25.50", "P2P", UUID.randomUUID().toString()).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/wallet/balance").with(customer(ana, "PERSONAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(74.50))
                .andExpect(jsonPath("$.pendingIncoming").value(0))
                .andExpect(jsonPath("$.currency").value("EUR"));
        mockMvc.perform(get("/api/v1/wallet/transactions").with(customer(ana, "PERSONAL")))
                .andExpect(jsonPath("$[0].type").value("PAYMENT"))
                .andExpect(jsonPath("$[0].direction").value("OUT"))
                .andExpect(jsonPath("$[0].amount").value(25.50))
                .andExpect(jsonPath("$[1].type").value("DEPOSIT"));
        mockMvc.perform(get("/api/v1/wallet/transactions").with(customer(besi, "PERSONAL")))
                .andExpect(jsonPath("$[0].direction").value("IN"))
                .andExpect(jsonPath("$[0].counterpartyName").exists());
    }

    @Test
    void walletIsCreatedLazilyOnFirstUse() throws Exception {
        UUID newUser = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/wallet/balance").with(customer(newUser, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(0))
                .andExpect(jsonPath("$.accountType").value("MERCHANT_WALLET"));
    }

    @Test
    void securityRules() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/balance"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/metrics").with(customer(UUID.randomUUID(), "PERSONAL")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void internalWalletCreationIsIdempotentAndProtected() throws Exception {
        UUID owner = UUID.randomUUID();
        String body = "{\"ownerId\":\"" + owner + "\",\"accountType\":\"MERCHANT\",\"displayName\":\"Shop\"}";

        String first = mockMvc.perform(post("/internal/wallets").header("X-Internal-Api-Key", "dev-internal-key-change-me")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("MERCHANT_WALLET"))
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/internal/wallets").header("X-Internal-Api-Key", "dev-internal-key-change-me")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        assertThat(second).isEqualTo(first);

        mockMvc.perform(post("/internal/wallets").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/wallets/" + UUID.randomUUID())
                        .header("X-Internal-Api-Key", "dev-internal-key-change-me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    private ResultActions pay(UUID payer, String payerType, UUID recipient, String amount, String type, String key)
            throws Exception {
        return mockMvc.perform(post("/api/v1/transactions/pay")
                .with(customer(payer, payerType))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(recipient, amount, type)));
    }

    private static String body(UUID recipient, String amount, String type) {
        return "{\"recipientId\":\"" + recipient + "\",\"amount\":" + amount
                + ",\"currency\":\"EUR\",\"transactionType\":\"" + type + "\",\"description\":\"test\"}";
    }
}
