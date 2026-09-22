CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE audit.external_api_call_log (
    id                      UUID NOT NULL,
    system_type             TEXT NOT NULL,
    endpoint                TEXT NOT NULL,
    http_method             TEXT NOT NULL,
    request_payload         JSONB NOT NULL ,
    response_payload        JSONB,
    http_status             INTEGER,
    submission_id           UUID,
    claim_id                UUID,
    created_on              TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_external_api_call_log PRIMARY KEY (id)
);

CREATE INDEX ix_external_api_call_log_claim_id ON audit.external_api_call_log(claim_id);
CREATE INDEX ix_external_api_call_log_submission_id ON audit.external_api_call_log(submission_id);
CREATE INDEX ix_external_api_call_log_system_type ON audit.external_api_call_log(system_type);
CREATE INDEX ix_external_api_call_log_created_on ON audit.external_api_call_log(created_on DESC);