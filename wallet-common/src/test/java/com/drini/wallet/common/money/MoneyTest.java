package com.drini.wallet.common.money;

import com.drini.wallet.common.error.WalletException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Checks the money helpers: amounts always have 2 decimals and are never rounded on the way in. */
class MoneyTest {

    @Test
    void positiveAmountGetsTwoDecimals() {
        assertThat(Money.requirePositiveAmount(new BigDecimal("10"))).isEqualByComparingTo("10.00");
        assertThat(Money.requirePositiveAmount(new BigDecimal("10")).scale()).isEqualTo(2);
    }

    @Test
    void zeroNegativeAndMissingAreRefused() {
        assertThatThrownBy(() -> Money.requirePositiveAmount(BigDecimal.ZERO)).isInstanceOf(WalletException.class);
        assertThatThrownBy(() -> Money.requirePositiveAmount(new BigDecimal("-1"))).isInstanceOf(WalletException.class);
        assertThatThrownBy(() -> Money.requirePositiveAmount(null)).isInstanceOf(WalletException.class);
    }

    @Test
    void moreThanTwoDecimalsIsRefused() {
        assertThatThrownBy(() -> Money.requirePositiveAmount(new BigDecimal("10.005")))
                .isInstanceOf(WalletException.class);
    }

    @Test
    void trailingZerosAreAccepted() {
        assertThat(Money.requirePositiveAmount(new BigDecimal("10.500"))).isEqualByComparingTo("10.50");
    }

    @Test
    void normalizeAndZero() {
        assertThat(Money.normalize(null)).isEqualTo(Money.zero());
        assertThat(Money.normalize(new BigDecimal("5")).toPlainString()).isEqualTo("5.00");
        assertThat(Money.zero().toPlainString()).isEqualTo("0.00");
    }
}
