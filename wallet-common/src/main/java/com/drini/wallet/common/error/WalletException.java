package com.drini.wallet.common.error;

/** The one exception type the services throw for expected problems ("insufficient funds", "link expired", ...). */
public class WalletException extends RuntimeException {

    private final ErrorCode errorCode;

    public WalletException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public WalletException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
