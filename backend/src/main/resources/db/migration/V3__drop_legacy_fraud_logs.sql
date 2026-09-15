-- V3: Drop legacy fraud_logs table
-- The legacy FraudDetectionService pipeline was removed in favor of the
-- statistical risk engine (risk_evaluations / risk_cases). See V1 for the
-- original table definition (left untouched to preserve the migration checksum).
DROP TABLE IF EXISTS fraud_logs;