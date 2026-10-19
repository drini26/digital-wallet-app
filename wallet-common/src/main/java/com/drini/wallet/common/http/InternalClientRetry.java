package com.drini.wallet.common.http;

import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.function.Supplier;

/** A plain retry loop for calls between our services (auth -> ledger, banking -> ledger, ledger -> banking). */
@Component
public class InternalClientRetry {

    private static final Logger log = LoggerFactory.getLogger(InternalClientRetry.class);

    static final int MAX_RETRIES = 3;
    static final long FIRST_WAIT_MILLIS = 200;

    public <T> T call(String description, Supplier<T> call) {
        long waitMillis = FIRST_WAIT_MILLIS;
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
            try {
                return call.get();
            } catch (ResourceAccessException | HttpServerErrorException e) {
                lastError = e;
                log.warn("{} failed (attempt {} of {}): {}", description, attempt, MAX_RETRIES + 1, e.getMessage());
            }

            if (attempt <= MAX_RETRIES) {
                sleep(waitMillis);
                waitMillis = waitMillis * 2;
            }
        }
        throw new WalletException(ErrorCode.SERVICE_UNAVAILABLE, description + " is unavailable, try again later",
                lastError);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
