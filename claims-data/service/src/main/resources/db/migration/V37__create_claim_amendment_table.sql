CREATE TABLE claim_amendment (
    claim_amendment_id UUID PRIMARY KEY,
    claim_id UUID NOT NULL,
    created_by_user_id TEXT NOT NULL,
    created_on TIMESTAMPTZ NOT NULL,
    updated_by_user_id TEXT,
    updated_on TIMESTAMPTZ,
    status TEXT NOT NULL CHECK (status IN ('READY_FOR_VALIDATION', 'VALID', 'INVALID')),
    changed_fields JSONB NOT NULL,
    CONSTRAINT fk_claim_amendment_claim_id FOREIGN KEY (claim_id) REFERENCES claim(id)
);

CREATE INDEX ix_claim_amendment_claim_id ON claim_amendment(claim_id);

COMMENT ON COLUMN claim_amendment.claim_amendment_id IS 'Unique identifier for the amendment record';
COMMENT ON COLUMN claim_amendment.claim_id IS 'Reference to the Claim being amended';
COMMENT ON COLUMN claim_amendment.created_by_user_id IS 'User ID of the caseworker who requested the amendment';
COMMENT ON COLUMN claim_amendment.created_on IS 'Timestamp when the amendment was created';
COMMENT ON COLUMN claim_amendment.updated_by_user_id IS 'User ID of the user who last modified the record';
COMMENT ON COLUMN claim_amendment.updated_on IS 'Timestamp of the last modification';
COMMENT ON COLUMN claim_amendment.status IS 'Current status: READY_FOR_VALIDATION, VALID, INVALID';
COMMENT ON COLUMN claim_amendment.changed_fields IS 'JSON blob capturing all changes and notes';

