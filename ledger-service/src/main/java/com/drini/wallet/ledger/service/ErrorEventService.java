package com.drini.wallet.ledger.service;

import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.ErrorEventRecorder;
import com.drini.wallet.common.error.WalletException;
import com.drini.wallet.ledger.api.dto.ErrorEventResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The managers' error log (table error_events): saved by ApiErrorHandler, read on the admin dashboard. */
@Service
public class ErrorEventService implements ErrorEventRecorder {

    private final JdbcTemplate jdbc;
    private final String serviceName;

    public ErrorEventService(JdbcTemplate jdbc, @Value("${spring.application.name}") String serviceName) {
        this.jdbc = jdbc;
        this.serviceName = serviceName;
    }

    @Override
    public void record(int httpStatus, String code, String message, String path) {
        jdbc.update("INSERT INTO error_events (service, http_status, code, message, path) VALUES (?, ?, ?, ?, ?)",
                serviceName, httpStatus, code, cut(message, 500), cut(path, 200));
    }

    public List<ErrorEventResponse> recent(int page, int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw new WalletException(ErrorCode.VALIDATION_FAILED, "page must be 0 or more and size 1 to 200");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, occurred_at, service, http_status, code, message, path FROM error_events "
                        + "ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?", size, (long) page * size);
        List<ErrorEventResponse> events = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            ErrorEventResponse event = new ErrorEventResponse();
            event.setId(((Number) row.get("id")).longValue());
            event.setOccurredAt(((Timestamp) row.get("occurred_at")).toInstant());
            event.setService((String) row.get("service"));
            event.setHttpStatus(((Number) row.get("http_status")).intValue());
            event.setCode((String) row.get("code"));
            event.setMessage((String) row.get("message"));
            event.setPath((String) row.get("path"));
            events.add(event);
        }
        return events;
    }

    /** 5xx answers since the given moment (systemErrorCount of the admin metrics). */
    public long countServerErrorsSince(Instant since) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM error_events WHERE http_status >= 500 AND occurred_at >= ?",
                Long.class, Timestamp.from(since));
        return count == null ? 0 : count;
    }

    private static String cut(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
