CREATE TABLE claim_case (
    id                                          UUID NOT NULL,
    claim_id                                    UUID NOT NULL,
    case_id                                     TEXT,
    unique_case_id                              TEXT,
    case_stage_code                             TEXT,
    stage_reached_code                          TEXT,
    standard_fee_category_code                  TEXT,
    outcome_code                                TEXT NOT NULL,
    designated_accredited_representative_code   TEXT,
    is_postal_application_accepted              BOOLEAN,
    is_client_2_postal_application_accepted     BOOLEAN,
    mental_health_tribunal_reference            TEXT,
    is_nrm_advice                               BOOLEAN,
    follow_on_work                              TEXT,
    transfer_date                               DATE,
    exemption_criteria_satisfied                TEXT,
    exceptional_case_funding_reference          TEXT,
    is_legacy_case                              BOOLEAN,
    created_by_user_id                          TEXT NOT NULL,
    created_on                                  TIMESTAMPTZ NOT NULL,
    updated_by_user_id                          TEXT,
    updated_on                                  TIMESTAMPTZ,

    CONSTRAINT pk_claim_case PRIMARY KEY (id),
    CONSTRAINT fk_claim_case_claim_id FOREIGN KEY (claim_id) REFERENCES claim(id)
);
CREATE INDEX ix_claim_case_claim_id ON claim_case(claim_id);