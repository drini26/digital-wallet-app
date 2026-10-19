package com.drini.wallet.common.security;

import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.ProblemJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** Answers the 401 and 403 of Spring Security in our JSON error format instead of an empty body. */
public class SecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ProblemJson.write(response, ErrorCode.UNAUTHENTICATED, "A valid bearer token is required");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ProblemJson.write(response, ErrorCode.FORBIDDEN, "You are not allowed to do this");
    }
}
