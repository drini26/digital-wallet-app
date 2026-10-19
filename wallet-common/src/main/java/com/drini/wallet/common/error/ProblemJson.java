package com.drini.wallet.common.error;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Writes the error JSON directly to a servlet response. */
public final class ProblemJson {

    private ProblemJson() {
    }

    public static void write(HttpServletResponse response, ErrorCode code, String detail) throws IOException {
        response.setStatus(code.getStatus());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        String json = "{\"type\":\"about:blank\""
                + ",\"title\":\"" + code.getTitle() + "\""
                + ",\"status\":" + code.getStatus()
                + ",\"detail\":\"" + detail + "\""
                + ",\"code\":\"" + code.name() + "\"}";
        response.getWriter().write(json);
    }
}
