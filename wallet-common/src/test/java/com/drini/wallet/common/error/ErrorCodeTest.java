package com.drini.wallet.common.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the mapping from HTTP statuses (raised by Spring itself) to our error codes. */
class ErrorCodeTest {

    @Test
    void mapsHttpStatusesToCodes() {
        assertThat(ErrorCode.fromHttpStatus(401)).isEqualTo(ErrorCode.UNAUTHENTICATED);
        assertThat(ErrorCode.fromHttpStatus(403)).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(ErrorCode.fromHttpStatus(404)).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(ErrorCode.fromHttpStatus(429)).isEqualTo(ErrorCode.RATE_LIMITED);
        assertThat(ErrorCode.fromHttpStatus(503)).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
        assertThat(ErrorCode.fromHttpStatus(500)).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(ErrorCode.fromHttpStatus(405)).isEqualTo(ErrorCode.MALFORMED_REQUEST);
    }

    @Test
    void codeHasStatusAndTitle() {
        assertThat(ErrorCode.INSUFFICIENT_FUNDS.getStatus()).isEqualTo(422);
        assertThat(ErrorCode.INSUFFICIENT_FUNDS.getTitle()).isEqualTo("Insufficient funds");
    }
}
