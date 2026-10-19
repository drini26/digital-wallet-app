package com.drini.wallet.ledger;

import com.drini.wallet.ledger.service.SystemControlService;
import com.drini.wallet.ledger.support.LedgerTestConfig;
import com.drini.wallet.ledger.support.TestWallets;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The calls banking-service makes on the ledger during a bank transfer (holds, incoming, settled, failed, system
 * status).
 */
@SpringBootTest(properties = "wallet.jobs.enabled=false")
@AutoConfigureMockMvc
@Import(LedgerTestConfig.class)
class InternalTransferTest {

    private static final String KEY = "dev-internal-key-change-me";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestWallets testWallets;
    @Autowired
    private SystemControlService systemControl;

    @Test
    void holdReservesTheMoney() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "100.00");
        UUID walletId = testWallets.walletIdOf(ana);
        UUID transferId = UUID.randomUUID();

        hold(transferId, walletId, "40.00").andExpect(status().isOk()).andExpect(jsonPath("$.availableBalance").value(60.00));
        hold(transferId, walletId, "40.00").andExpect(status().isOk()).andExpect(jsonPath("$.availableBalance").value(60.00));

        assertThat(testWallets.balanceOf(ana)).isEqualByComparingTo("60.00");
        mockMvc.perform(get("/api/v1/wallet/balance").with(customer(ana, "PERSONAL")))
                .andExpect(jsonPath("$.pendingOutgoing").value(40.00))
                .andExpect(jsonPath("$.pendingBalance").value(40.00));
    }

    @Test
    void holdNeedsEnoughMoney() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "10.00");

        hold(UUID.randomUUID(), testWallets.walletIdOf(ana), "10.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void noHoldWhileFrozen() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "10.00");
        systemControl.freeze("test");
        try {
            hold(UUID.randomUUID(), testWallets.walletIdOf(ana), "1.00")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("SYSTEM_FROZEN"));
            mockMvc.perform(get("/internal/system-status").header("X-Internal-Api-Key", KEY))
                    .andExpect(jsonPath("$.outgoingFrozen").value(true));
        } finally {
            systemControl.unfreeze(UUID.randomUUID());
        }
        mockMvc.perform(get("/internal/system-status").header("X-Internal-Api-Key", KEY))
                .andExpect(jsonPath("$.outgoingFrozen").value(false));
    }

    @Test
    void settledWithdrawalLeavesTheCompany() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "100.00");
        UUID walletId = testWallets.walletIdOf(ana);
        UUID transferId = UUID.randomUUID();
        hold(transferId, walletId, "40.00");

        notice(transferId, "settled", "OUTFLOW", walletId, "40.00").andExpect(status().isNoContent());
        notice(transferId, "settled", "OUTFLOW", walletId, "40.00").andExpect(status().isNoContent());

        assertThat(testWallets.balanceOf(ana)).isEqualByComparingTo("60.00");
        mockMvc.perform(get("/api/v1/wallet/balance").with(customer(ana, "PERSONAL")))
                .andExpect(jsonPath("$.pendingOutgoing").value(0));
    }

    @Test
    void rejectedWithdrawalReturnsTheMoney() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "100.00");
        UUID walletId = testWallets.walletIdOf(ana);
        UUID transferId = UUID.randomUUID();
        hold(transferId, walletId, "40.00");

        notice(transferId, "failed", "OUTFLOW", walletId, "40.00").andExpect(status().isNoContent());
        notice(transferId, "failed", "OUTFLOW", walletId, "40.00").andExpect(status().isNoContent());

        assertThat(testWallets.balanceOf(ana)).isEqualByComparingTo("100.00");
        mockMvc.perform(get("/api/v1/wallet/transactions").with(customer(ana, "PERSONAL")))
                .andExpect(jsonPath("$[0].type").value("WITHDRAWAL_RELEASED"))
                .andExpect(jsonPath("$[0].direction").value("IN"));
    }

    @Test
    void depositArrivesWhenSettled() throws Exception {
        UUID besi = testWallets.newUser("PERSONAL", "0");
        UUID walletId = testWallets.walletIdOf(besi);
        UUID transferId = UUID.randomUUID();

        notice(transferId, "incoming", "INFLOW", walletId, "75.00").andExpect(status().isNoContent());
        notice(transferId, "incoming", "INFLOW", walletId, "75.00").andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/wallet/balance").with(customer(besi, "PERSONAL")))
                .andExpect(jsonPath("$.pendingIncoming").value(75.00))
                .andExpect(jsonPath("$.pendingBalance").value(75.00))
                .andExpect(jsonPath("$.availableBalance").value(0));

        notice(transferId, "settled", "INFLOW", walletId, "75.00").andExpect(status().isNoContent());
        notice(transferId, "settled", "INFLOW", walletId, "75.00").andExpect(status().isNoContent());

        assertThat(testWallets.balanceOf(besi)).isEqualByComparingTo("75.00");
        mockMvc.perform(get("/api/v1/wallet/balance").with(customer(besi, "PERSONAL")))
                .andExpect(jsonPath("$.pendingIncoming").value(0));
    }

    @Test
    void failedDepositMovesNoMoney() throws Exception {
        UUID besi = testWallets.newUser("PERSONAL", "0");
        UUID walletId = testWallets.walletIdOf(besi);
        UUID transferId = UUID.randomUUID();
        notice(transferId, "incoming", "INFLOW", walletId, "75.00");

        notice(transferId, "failed", "INFLOW", walletId, "75.00").andExpect(status().isNoContent());

        assertThat(testWallets.balanceOf(besi)).isEqualByComparingTo("0.00");
    }

    @Test
    void walletOwnerCanBeChecked() throws Exception {
        UUID ana = testWallets.newUser("PERSONAL", "0");

        mockMvc.perform(get("/internal/wallets/" + testWallets.walletIdOf(ana)).header("X-Internal-Api-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(ana.toString()));
    }

    private ResultActions hold(UUID transferId, UUID walletId, String amount) throws Exception {
        return mockMvc.perform(post("/internal/holds").header("X-Internal-Api-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"transferId\":\"" + transferId + "\",\"walletId\":\"" + walletId + "\",\"amount\":" + amount
                        + ",\"estimatedArrival\":\"2026-10-20\"}"));
    }

    private ResultActions notice(UUID transferId, String what, String direction, UUID walletId, String amount)
            throws Exception {
        return mockMvc.perform(post("/internal/transfers/" + transferId + "/" + what).header("X-Internal-Api-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"direction\":\"" + direction + "\",\"walletId\":\"" + walletId + "\",\"amount\":" + amount + "}"));
    }
}
