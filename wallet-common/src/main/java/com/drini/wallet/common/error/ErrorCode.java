package com.drini.wallet.common.error;

/** Every error the wallet can answer, with its HTTP status and a short title. */
public enum ErrorCode {

    VALIDATION_FAILED(400, "Validation failed"),
    MALFORMED_REQUEST(400, "Malformed request"),
    IDEMPOTENCY_KEY_REQUIRED(400, "Idempotency key required"),

    UNAUTHENTICATED(401, "Authentication required"),
    MAGIC_LINK_INVALID(401, "Invalid sign-in link"),
    MAGIC_LINK_EXPIRED(401, "Sign-in link expired"),
    MAGIC_LINK_USED(401, "Sign-in link already used"),
    FORBIDDEN(403, "Forbidden"),

    NOT_FOUND(404, "Not found"),
    RECIPIENT_NOT_FOUND(404, "Recipient not found"),
    WALLET_NOT_FOUND(404, "Wallet not found"),
    TRANSACTION_NOT_FOUND(404, "Transaction not found"),
    TRANSFER_NOT_FOUND(404, "Transfer not found"),

    IDEMPOTENCY_KEY_REUSED(409, "Idempotency key reused with a different request"),
    ALREADY_REVERSED(409, "Transaction already reversed"),

    INSUFFICIENT_FUNDS(422, "Insufficient funds"),
    SYSTEM_FROZEN(422, "Outgoing money is frozen"),
    SELF_PAYMENT_NOT_ALLOWED(422, "You cannot pay yourself"),
    UNSUPPORTED_CURRENCY(422, "Unsupported currency"),
    AMOUNT_LIMIT_EXCEEDED(422, "Amount limit exceeded"),
    RECIPIENT_TYPE_MISMATCH(422, "Recipient type does not match the payment type"),
    NOT_REVERSIBLE(422, "Transaction cannot be reversed"),
    NOTHING_TO_SWEEP(422, "Nothing to sweep"),
    INVALID_ROUTING_NUMBER(422, "Invalid routing number"),

    RATE_LIMITED(429, "Too many requests"),
    INTERNAL_ERROR(500, "Internal error"),
    LOCK_TIMEOUT(503, "Account busy, please retry"),
    SERVICE_UNAVAILABLE(503, "Service unavailable");

    private final int status;
    private final String title;

    ErrorCode(int status, String title) {
        this.status = status;
        this.title = title;
    }

    public int getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public static ErrorCode fromHttpStatus(int status) {
        if (status == 401) {
            return UNAUTHENTICATED;
        }
        if (status == 403) {
            return FORBIDDEN;
        }
        if (status == 404) {
            return NOT_FOUND;
        }
        if (status == 429) {
            return RATE_LIMITED;
        }
        if (status == 503) {
            return SERVICE_UNAVAILABLE;
        }
        if (status >= 500) {
            return INTERNAL_ERROR;
        }
        return MALFORMED_REQUEST;
    }
}
