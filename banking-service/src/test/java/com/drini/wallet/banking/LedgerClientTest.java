package com.drini.wallet.banking;

import com.drini.wallet.banking.client.LedgerClient;
import com.drini.wallet.banking.domain.BankTransfer;
import com.drini.wallet.banking.domain.TransferDirection;
import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.common.http.InternalClientRetry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Checks the HTTP calls banking makes on the ledger with a fake server (MockRestServiceServer). */
class LedgerClientTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer ledgerServer = MockRestServiceServer.bindTo(builder).build();
    private final LedgerClient client = new LedgerClient(builder, new InternalClientRetry(), "http://ledger", "the-key");

    private final BankTransfer transfer = new BankTransfer(UUID.randomUUID(), UUID.randomUUID(),
            TransferDirection.OUTFLOW, new BigDecimal("40.00"), "021000021", "123456789", "key");

    @Test
    void readsTheWallet() {
        UUID walletId = UUID.randomUUID();
        ledgerServer.expect(requestTo("http://ledger/internal/wallets/" + walletId))
                .andExpect(header("X-Internal-Api-Key", "the-key"))
                .andRespond(withSuccess("{\"walletId\":\"" + walletId + "\",\"ownerId\":\"" + walletId + "\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.getWallet(walletId).getOwnerId()).isEqualTo(walletId);
    }

    @Test
    void sendsHoldAndNotices() {
        ledgerServer.expect(requestTo("http://ledger/internal/holds"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.transferId").value(transfer.getId().toString()))
                .andExpect(jsonPath("$.amount").value(40.00))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        ledgerServer.expect(requestTo("http://ledger/internal/transfers/" + transfer.getId() + "/settled"))
                .andExpect(jsonPath("$.direction").value("OUTFLOW"))
                .andRespond(withNoContent());
        ledgerServer.expect(requestTo("http://ledger/internal/transfers/" + transfer.getId() + "/failed"))
                .andRespond(withNoContent());
        ledgerServer.expect(requestTo("http://ledger/internal/transfers/" + transfer.getId() + "/incoming"))
                .andRespond(withNoContent());

        client.hold(transfer, LocalDate.now());
        client.settled(transfer);
        client.failed(transfer);
        client.incoming(transfer, LocalDate.now());
        ledgerServer.verify();
    }

    @Test
    void ledgerBusinessErrorKeepsItsCode() {
        ledgerServer.expect(requestTo("http://ledger/internal/holds"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"status\":422,\"detail\":\"Your balance is not enough\",\"code\":\"INSUFFICIENT_FUNDS\"}"));

        assertThatThrownBy(() -> client.hold(transfer, LocalDate.now()))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
    }

    @Test
    void readsTheFreezeFlag() {
        ledgerServer.expect(requestTo("http://ledger/internal/system-status"))
                .andRespond(withSuccess("{\"outgoingFrozen\":true}", MediaType.APPLICATION_JSON));

        assertThat(client.isOutgoingFrozen()).isTrue();
    }
}
