package com.drini.wallet.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Checks that /internal/** needs the shared key, and that normal paths are not affected. */
class InternalApiKeyFilterTest {

    private final InternalApiKeyFilter filter = new InternalApiKeyFilter("the-key");

    @Test
    void rightKeyPasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/holds");
        request.addHeader(InternalApiKeyFilter.HEADER, "the-key");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void missingOrWrongKeyIsRefused() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/holds");
        request.addHeader(InternalApiKeyFilter.HEADER, "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHENTICATED\"");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void otherPathsAreNotChecked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/wallet/balance");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void emptyKeyIsAConfigurationError() {
        assertThatThrownBy(() -> new InternalApiKeyFilter(" ")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new InternalApiKeyFilter(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void securityRefusalsUseTheErrorFormat() throws Exception {
        SecurityErrorHandlers handlers = new SecurityErrorHandlers();
        MockHttpServletResponse unauthenticated = new MockHttpServletResponse();
        MockHttpServletResponse forbidden = new MockHttpServletResponse();

        handlers.commence(new MockHttpServletRequest(), unauthenticated, null);
        handlers.handle(new MockHttpServletRequest(), forbidden, null);

        assertThat(unauthenticated.getStatus()).isEqualTo(401);
        assertThat(forbidden.getStatus()).isEqualTo(403);
        assertThat(forbidden.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
    }
}
