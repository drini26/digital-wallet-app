-- The business day a run is for and the pending withdrawals it counted, so the history can show the total
-- system balance. Both are NULL for runs made before this migration.
ALTER TABLE reconciliation_runs ADD COLUMN target_date DATE;
ALTER TABLE reconciliation_runs ADD COLUMN outgoing_pending NUMERIC(19, 2);
