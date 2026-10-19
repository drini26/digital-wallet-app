package com.drini.wallet.gateway.ratelimit;

import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.ProblemJson;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Limits how often one client can call POST /auth/register and POST /auth/magic-link. */
@Component
@Order(-200)
public class SignInRateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> bucketsByClient = new ConcurrentHashMap<>();
    private final int requestsPerMinute;

    public SignInRateLimitFilter(@Value("${wallet.gateway.sign-in-requests-per-minute}") int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean signInPath = path.equals("/api/v1/auth/register") || path.equals("/api/v1/auth/magic-link");
        return !("POST".equals(request.getMethod()) && signInPath);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Bucket bucket = bucketsByClient.computeIfAbsent(clientKey(request), key -> newBucket());
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }

        response.setHeader("Retry-After", "60");
        ProblemJson.write(response, ErrorCode.RATE_LIMITED, "Too many requests. Please wait a minute and try again.");
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
