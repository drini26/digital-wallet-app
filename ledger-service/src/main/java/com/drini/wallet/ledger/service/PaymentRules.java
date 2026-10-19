package com.drini.wallet.ledger.service;

import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.common.money.Money;
import com.drini.wallet.ledger.domain.Account;
import com.drini.wallet.ledger.domain.AccountType;
import com.drini.wallet.ledger.domain.PaymentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** The business rules a payment must pass. */
@Component
public class PaymentRules {

    private final BigDecimal maxPaymentAmount;

    public PaymentRules(@Value("${wallet.ledger.max-payment-amount}") BigDecimal maxPaymentAmount) {
        this.maxPaymentAmount = maxPaymentAmount;
    }

    public void check(String currency, BigDecimal amount, PaymentType paymentType, Account payer, Account payee) {
        checkCurrency(currency);
        checkAmountLimit(amount);
        checkNotSelf(payer, payee);
        checkRecipientType(paymentType, payee);
        checkSufficientFunds(payer, amount);
    }

    private void checkCurrency(String currency) {
        if (!Money.EUR.equals(currency)) {
            throw new WalletException(ErrorCode.UNSUPPORTED_CURRENCY, "Only EUR is supported");
        }
    }

    private void checkAmountLimit(BigDecimal amount) {
        if (amount.compareTo(maxPaymentAmount) > 0) {
            throw new WalletException(ErrorCode.AMOUNT_LIMIT_EXCEEDED,
                    "A single payment can be at most " + maxPaymentAmount.toPlainString() + " EUR");
        }
    }

    private void checkNotSelf(Account payer, Account payee) {
        if (payer.getId().equals(payee.getId())) {
            throw new WalletException(ErrorCode.SELF_PAYMENT_NOT_ALLOWED, "You cannot pay yourself");
        }
    }

    private void checkRecipientType(PaymentType paymentType, Account payee) {
        AccountType expected = AccountType.PERSONAL_WALLET;
        if (paymentType == PaymentType.MERCHANT) {
            expected = AccountType.MERCHANT_WALLET;
        }
        if (payee.getType() != expected) {
            throw new WalletException(ErrorCode.RECIPIENT_TYPE_MISMATCH,
                    "A " + paymentType + " payment cannot go to a " + payee.getType() + " account");
        }
    }

    private void checkSufficientFunds(Account payer, BigDecimal amount) {
        if (payer.getBalance().compareTo(amount) < 0) {
            throw new WalletException(ErrorCode.INSUFFICIENT_FUNDS,
                    "Your balance of " + payer.getBalance().toPlainString() + " EUR is not enough for this payment");
        }
    }
}
