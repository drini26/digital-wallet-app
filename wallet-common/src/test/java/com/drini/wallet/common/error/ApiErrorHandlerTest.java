package com.drini.wallet.common.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checks that every kind of error is answered in the same JSON shape with the right status and "code". */
class ApiErrorHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new ApiErrorHandler())
                .build();
    }

    @Test
    void businessErrorKeepsItsCode() throws Exception {
        mockMvc.perform(get("/business"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.detail").value("Not enough money"));
    }

    @Test
    void serverSideBusinessErrorIsAnswered() throws Exception {
        mockMvc.perform(get("/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void invalidBodyListsTheFields() throws Exception {
        mockMvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0]").exists());
    }

    @Test
    void brokenJsonIsMalformed() throws Exception {
        mockMvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void wrongParameterTypeIsValidationError() throws Exception {
        mockMvc.perform(get("/items/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void missingIdempotencyKeyHasItsOwnCode() throws Exception {
        mockMvc.perform(get("/needs-key"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void otherMissingHeaderIsValidationError() throws Exception {
        mockMvc.perform(get("/needs-other-header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void lockTimeoutIsRetryable() throws Exception {
        mockMvc.perform(get("/lock"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LOCK_TIMEOUT"));
    }

    @Test
    void accessDeniedIsForbidden() throws Exception {
        mockMvc.perform(get("/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unexpectedErrorIsGeneric() throws Exception {
        mockMvc.perform(get("/bug"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void serverErrorsAndFreezesGoToTheErrorLog() throws Exception {
        RecordingRecorder recorder = new RecordingRecorder();
        MockMvc withLog = withRecorder(recorder);

        withLog.perform(get("/unavailable")).andExpect(status().isServiceUnavailable());
        withLog.perform(get("/lock")).andExpect(status().isServiceUnavailable());
        withLog.perform(get("/bug")).andExpect(status().isInternalServerError());
        withLog.perform(get("/frozen")).andExpect(status().isUnprocessableEntity());
        withLog.perform(get("/business")).andExpect(status().isUnprocessableEntity());

        assertThat(recorder.events).containsExactly(
                "503 SERVICE_UNAVAILABLE /unavailable", "503 LOCK_TIMEOUT /lock",
                "500 INTERNAL_ERROR /bug", "422 SYSTEM_FROZEN /frozen");
    }

    @Test
    void brokenErrorLogDoesNotChangeTheAnswer() throws Exception {
        ErrorEventRecorder broken = (httpStatus, code, message, path) -> {
            throw new IllegalStateException("database down");
        };

        withRecorder(broken).perform(get("/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void springErrorsKeepTheirStatus() throws Exception {
        mockMvc.perform(post("/bug"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private MockMvc withRecorder(ErrorEventRecorder recorder) {
        ApiErrorHandler handler = new ApiErrorHandler();
        handler.setRecorder(recorder);
        return MockMvcBuilders.standaloneSetup(new FailingController()).setControllerAdvice(handler).build();
    }

    static class RecordingRecorder implements ErrorEventRecorder {
        private final List<String> events = new ArrayList<>();

        @Override
        public void record(int httpStatus, String code, String message, String path) {
            events.add(httpStatus + " " + code + " " + path);
        }
    }

    static class NameBody {
        @NotBlank
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @RestController
    static class FailingController {

        @GetMapping("/business")
        void business() {
            throw new WalletException(ErrorCode.INSUFFICIENT_FUNDS, "Not enough money");
        }

        @GetMapping("/frozen")
        void frozen() {
            throw new WalletException(ErrorCode.SYSTEM_FROZEN, "Outgoing money is frozen");
        }

        @GetMapping("/unavailable")
        void unavailable() {
            throw new WalletException(ErrorCode.SERVICE_UNAVAILABLE, "ledger is down");
        }

        @PostMapping("/body")
        void body(@Valid @RequestBody NameBody body) {
        }

        @GetMapping("/items/{id}")
        void item(@PathVariable UUID id) {
        }

        @GetMapping("/needs-key")
        void needsKey(@RequestHeader("Idempotency-Key") String key) {
        }

        @GetMapping("/needs-other-header")
        void needsOther(@RequestHeader("X-Other") String other) {
        }

        @GetMapping("/lock")
        void lock() {
            throw new PessimisticLockingFailureException("lock timeout");
        }

        @GetMapping("/denied")
        void denied() {
            throw new AccessDeniedException("no");
        }

        @GetMapping("/bug")
        void bug() {
            throw new IllegalStateException("secret internal detail");
        }
    }
}
