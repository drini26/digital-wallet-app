-- Error log for managers: every 5xx answer, plus refusals caused by a freeze or a lock timeout.
CREATE TABLE error_events (
    id          BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    service     VARCHAR(32)  NOT NULL,
    http_status INT          NOT NULL,
    code        VARCHAR(40)  NOT NULL,
    message     VARCHAR(500),
    path        VARCHAR(200)
);
-- Newest first for the error log, and the 24-hour count of the metrics.
CREATE INDEX ix_error_events_occurred_at ON error_events (occurred_at DESC);
