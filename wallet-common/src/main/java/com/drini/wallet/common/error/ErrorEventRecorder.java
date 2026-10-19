package com.drini.wallet.common.error;

/** Saves an error event for the managers' error log. Implemented by the service that owns the table (ledger). */
public interface ErrorEventRecorder {

    void record(int httpStatus, String code, String message, String path);
}
