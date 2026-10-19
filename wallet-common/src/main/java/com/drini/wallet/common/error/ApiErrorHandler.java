package com.drini.wallet.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;

/** Turns every exception into the same JSON error shape (RFC 7807 ProblemDetail plus a "code" field). */
@RestControllerAdvice
public class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    private ErrorEventRecorder recorder;

    /** Optional: only ledger-service has an error_events table; the other services just answer. */
    @Autowired(required = false)
    public void setRecorder(ErrorEventRecorder recorder) {
        this.recorder = recorder;
    }

    @ExceptionHandler(WalletException.class)
    public ResponseEntity<ProblemDetail> handleWalletException(WalletException ex, HttpServletRequest request) {
        if (ex.getErrorCode().getStatus() >= 500) {
            log.warn("Request failed with {}: {}", ex.getErrorCode(), ex.getMessage());
        }
        // A freeze is a 422, but managers must see every refusal it causes.
        if (ex.getErrorCode().getStatus() >= 500 || ex.getErrorCode() == ErrorCode.SYSTEM_FROZEN) {
            recordEvent(ex.getErrorCode().getStatus(), ex.getErrorCode(), ex.getMessage(), request);
        }
        return respond(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleInvalidBody(MethodArgumentNotValidException ex) {
        List<String> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
        }
        ResponseEntity<ProblemDetail> response = respond(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid");
        response.getBody().setProperty("errors", errors);
        return response;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return respond(ErrorCode.MALFORMED_REQUEST, "Request body is missing or is not valid JSON");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(ErrorCode.VALIDATION_FAILED, "Parameter '" + ex.getName() + "' has an invalid value");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException ex) {
        if ("Idempotency-Key".equalsIgnoreCase(ex.getHeaderName())) {
            return respond(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "The Idempotency-Key header is required");
        }
        return respond(ErrorCode.VALIDATION_FAILED, "Header '" + ex.getHeaderName() + "' is required");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleLockTimeout(PessimisticLockingFailureException ex,
                                                          HttpServletRequest request) {
        log.warn("Lock timeout: {}", ex.getMessage());
        recordEvent(ErrorCode.LOCK_TIMEOUT.getStatus(), ErrorCode.LOCK_TIMEOUT, "Lock timeout on a busy account", request);
        return respond(ErrorCode.LOCK_TIMEOUT, "The account is busy. Retry with the same Idempotency-Key.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return respond(ErrorCode.FORBIDDEN, "You are not allowed to do this");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse springError) {
            ErrorCode code = ErrorCode.fromHttpStatus(springError.getStatusCode().value());
            ProblemDetail body = springError.getBody();
            body.setProperty("code", code.name());
            if (springError.getStatusCode().is5xxServerError()) {
                recordEvent(springError.getStatusCode().value(), code, ex.getMessage(), request);
            }
            return ResponseEntity.status(springError.getStatusCode()).body(body);
        }
        log.error("Unexpected error", ex);
        recordEvent(500, ErrorCode.INTERNAL_ERROR, ex.getClass().getSimpleName() + ": " + ex.getMessage(), request);
        return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred");
    }

    private void recordEvent(int status, ErrorCode code, String message, HttpServletRequest request) {
        if (recorder == null) {
            return;
        }
        try {
            recorder.record(status, code.name(), message, request.getRequestURI());
        } catch (RuntimeException e) {
            log.warn("Could not save the error event: {}", e.getMessage());
        }
    }

    private ResponseEntity<ProblemDetail> respond(ErrorCode code, String detail) {
        ProblemDetail body = ProblemDetail.forStatus(code.getStatus());
        body.setTitle(code.getTitle());
        body.setDetail(detail);
        body.setProperty("code", code.name());
        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
