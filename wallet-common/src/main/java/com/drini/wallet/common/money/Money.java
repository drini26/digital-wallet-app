package com.drini.wallet.common.money;

import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Small helpers for amounts of money. */
public final class Money {

    public static final int SCALE = 2;

    public static final String EUR = "EUR";

    private Money() {
    }

    /** Rejects more than 2 decimals instead of rounding, so 10.005 is never silently changed. */
    public static BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new WalletException(ErrorCode.VALIDATION_FAILED, "amount must be greater than 0");
        }
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new WalletException(ErrorCode.VALIDATION_FAILED, "amount can have at most 2 decimals");
        }
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return zero();
        }
        return value.setScale(SCALE, RoundingMode.HALF_EVEN);
    }
}
