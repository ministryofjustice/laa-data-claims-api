CREATE TABLE claim (
    id                                       UUID NOT NULL,
    submission_id                            UUID NOT NULL,
    status                                   TEXT NOT NULL,
    schedule_reference                       TEXT NOT NULL,
    line_number                              INTEGER NOT NULL,
    case_reference_number                    TEXT NOT NULL,
    unique_file_number                       TEXT NOT NULL,
    case_start_date                          DATE NOT NULL,
    case_concluded_date                      DATE NOT NULL,
    matter_type_code                         TEXT NOT NULL,
    crime_matter_type_code                   TEXT,
    fee_scheme_code                          TEXT,
    fee_code                                 TEXT,
    procurement_area_code                    TEXT,
    access_point_code                        TEXT,
    delivery_location                        TEXT,
    representation_order_date                DATE,
    suspects_defendants_count                INTEGER,
    police_station_court_attendances_count   INTEGER,
    police_station_court_prison_id           TEXT,
    dscc_number                              TEXT,
    maat_id                                  TEXT,
    prison_law_prior_approval_number         TEXT,
    is_duty_solicitor                        BOOLEAN,
    is_youth_court                           BOOLEAN,
    scheme_id                                TEXT,
    mediation_sessions_count                 INTEGER,
    mediation_time_minutes                   INTEGER,
    outreach_location                        TEXT,
    referral_source                          TEXT,
    matched_claim_id                         UUID,
    total_value                              NUMERIC,
    created_by_user_id                       TEXT NOT NULL,
    created_on                               TIMESTAMPTZ NOT NULL,
    updated_by_user_id                       TEXT,
    updated_on                               TIMESTAMPTZ,

    CONSTRAINT pk_claim PRIMARY KEY (id),
    CONSTRAINT fk_claim_submission_id FOREIGN KEY (submission_id) REFERENCES submission(id),
    CONSTRAINT fk_claim_matched_claim_id FOREIGN KEY (matched_claim_id) REFERENCES claim(id),
    CONSTRAINT chk_claim_status CHECK (status IN ('READY_TO_PROCESS', 'VALID', 'INVALID'))
);

CREATE INDEX ix_claim_submission_id ON claim(submission_id);
CREATE INDEX ix_claim_matched_claim_id ON claim(matched_claim_id);

COMMENT ON COLUMN claim.matched_claim_id IS 'ID of claim of which this is found to be a duplicate';