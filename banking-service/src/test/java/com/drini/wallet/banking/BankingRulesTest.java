package com.drini.wallet.banking;

import com.drini.wallet.banking.domain.BankTransfer;
import com.drini.wallet.banking.domain.RoutingNumber;
import com.drini.wallet.banking.domain.TransferDirection;
import com.drini.wallet.banking.service.BusinessDays;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Small rules of banking-service without a database: routing numbers, arrival dates, allowed status changes. */
class BankingRulesTest {

    @Test
    void routingNumberChecksum() {
        assertThat(RoutingNumber.isValid("021000021")).isTrue();
        assertThat(RoutingNumber.isValid("011000015")).isTrue();
        assertThat(RoutingNumber.isValid("021000022")).isFalse();
        assertThat(RoutingNumber.isValid("02100002A")).isFalse();
        assertThat(RoutingNumber.isValid("12345")).isFalse();
        assertThat(RoutingNumber.isValid(null)).isFalse();
    }

    @Test
    void arrivalSkipsTheWeekend() {
        assertThat(BusinessDays.estimatedArrival(LocalDate.of(2026, 10, 16))).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(BusinessDays.estimatedArrival(LocalDate.of(2026, 10, 17))).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(BusinessDays.estimatedArrival(LocalDate.of(2026, 10, 20))).isEqualTo(LocalDate.of(2026, 10, 21));
    }

    @Test
    void statusCanOnlyMoveForward() {
        BankTransfer transfer = new BankTransfer(UUID.randomUUID(), UUID.randomUUID(), TransferDirection.OUTFLOW,
                new BigDecimal("10.00"), "021000021", "123456789", "key");

        assertThatThrownBy(() -> transfer.markSent(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        transfer.markQueued(LocalDate.now());
        transfer.markSent(UUID.randomUUID());
        transfer.markSettled();
        assertThatThrownBy(() -> transfer.markRejected("late")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> transfer.markFailed("late")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bankReferenceIsStable() {
        BankTransfer transfer = new BankTransfer(UUID.randomUUID(), UUID.randomUUID(), TransferDirection.INFLOW,
                new BigDecimal("10.00"), "021000021", "123456789", "key");

        assertThat(transfer.getBankReference()).isEqualTo(transfer.getId().toString());
    }
}
