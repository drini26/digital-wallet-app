package com.drini.wallet.banking.mockbank;

/** "The bank did not answer": the bank is offline, or it processed the transfer but the answer was lost. */
public class BankUnavailableException extends RuntimeException {

    public BankUnavailableException(String message) {
        super(message);
    }
}
