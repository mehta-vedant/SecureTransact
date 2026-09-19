ALTER TABLE risk_cases ADD COLUMN priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM';
ALTER TABLE risk_cases ADD COLUMN version BIGINT;
CREATE INDEX idx_risk_cases_priority_created ON risk_cases(priority, created_at);

CREATE TABLE risk_case_events (
    id BIGSERIAL PRIMARY KEY,
    risk_case_id BIGINT NOT NULL REFERENCES risk_cases(id),
    actor_user_id BIGINT REFERENCES users(id),
    type VARCHAR(32) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_risk_case_events_case_created ON risk_case_events(risk_case_id, created_at);
