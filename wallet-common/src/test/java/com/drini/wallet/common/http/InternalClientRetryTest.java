package com.drini.wallet.common.http;

import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Checks the retry loop used for calls between services. */
class InternalClientRetryTest {

    private final InternalClientRetry retry = new InternalClientRetry();

    @Test
    void retriesUntilTheCallSucceeds() {
        AtomicInteger attempts = new AtomicInteger();

        String result = retry.call("ledger: test", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new ResourceAccessException("connection refused");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void givesUpAfterThreeRetries() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retry.call("ledger: test", () -> {
            attempts.incrementAndGet();
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        }))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getErrorCode())
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
        assertThat(attempts.get()).isEqualTo(4);
    }

    @Test
    void doesNotRetryBusinessAnswers() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retry.call("ledger: test", () -> {
            attempts.incrementAndGet();
            throw new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY);
        })).isInstanceOf(HttpClientErrorException.class);
        assertThat(attempts.get()).isEqualTo(1);
    }
}
