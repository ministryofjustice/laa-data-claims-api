-- Governed reference data for claim amendment metadata (DSTEW-1594).
-- requested_by_reference holds the requesting party/type values.
-- amendment_reason_reference holds the reasons, scoped to the requested_by value they are valid for.
-- Ids are generated as UUIDv7 in the application layer (Uuid7.timeBasedUuid()); no DB default is set.
-- These tables are deliberately NOT added to the reporting publication for now.

CREATE TABLE requested_by_reference (
    id                      UUID NOT NULL,
    code                    VARCHAR NOT NULL,
    display_label           VARCHAR NOT NULL,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    display_order           INTEGER NOT NULL,
    created_by_user_id      TEXT NOT NULL,
    created_on              TIMESTAMPTZ NOT NULL,
    updated_by_user_id      TEXT,
    updated_on              TIMESTAMPTZ,
    CONSTRAINT pk_requested_by_reference PRIMARY KEY (id),
    CONSTRAINT uq_requested_by_reference_code UNIQUE (code)
);

CREATE TABLE amendment_reason_reference (
    id                      UUID NOT NULL,
    requested_by_code       VARCHAR NOT NULL,
    code                    VARCHAR NOT NULL,
    display_label           VARCHAR NOT NULL,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    display_order           INTEGER NOT NULL,
    created_by_user_id      TEXT NOT NULL,
    created_on              TIMESTAMPTZ NOT NULL,
    updated_by_user_id      TEXT,
    updated_on              TIMESTAMPTZ,
    CONSTRAINT pk_amendment_reason_reference PRIMARY KEY (id),
    CONSTRAINT uq_amendment_reason_reference_party_code UNIQUE (requested_by_code, code),
    CONSTRAINT fk_amendment_reason_requested_by_code
        FOREIGN KEY (requested_by_code)
        REFERENCES requested_by_reference (code)
        ON DELETE RESTRICT
);

CREATE INDEX ix_amendment_reason_reference_requested_by_code
    ON amendment_reason_reference (requested_by_code);

-- Seed the BC-574 confirmed amendment reference data (DSTEW-1594).
-- Only the three BC-574 confirmed Requested By parties are seeded here; the final list
-- (e.g. Auditor) is expected from the business requirement and will be added via a later migration.
-- Ids below are pre-generated UUIDv7 values. The seed actor is supplied via the Flyway
-- placeholder ${amendment_reference_seed_actor} (configured in application.yml).

-- Requested By values
INSERT INTO requested_by_reference
(id, code, display_label, is_active, display_order, created_by_user_id, created_on)
VALUES
    ('01890a5d-ac96-774b-9c00-000000000001', 'PROVIDER', 'Provider', TRUE, 10,
     '${amendment_reference_seed_actor}', now()),
    ('01890a5d-ac96-774b-9c00-000000000002', 'CONTRACT_MANAGEMENT', 'Contract Management', TRUE, 20,
     '${amendment_reference_seed_actor}', now()),
    ('01890a5d-ac96-774b-9c00-000000000003', 'ASSURANCE', 'Assurance', TRUE, 30,
     '${amendment_reference_seed_actor}', now());

-- Amendment Reason values, scoped to the Requested By value they are valid for
INSERT INTO amendment_reason_reference
(id, requested_by_code, code, display_label, is_active, display_order, created_by_user_id, created_on)
VALUES
    -- Provider
    ('01890a5d-ac96-774b-9c00-000000000101', 'PROVIDER', 'PROVIDER_ERROR',
     'Provider Error', TRUE, 10, '${amendment_reference_seed_actor}', now()),
    ('01890a5d-ac96-774b-9c00-000000000102', 'PROVIDER', 'CASE_REOPENED_REBILLED',
     'Case re-opened and being billed again later', TRUE, 20, '${amendment_reference_seed_actor}', now()),
    ('01890a5d-ac96-774b-9c00-000000000103', 'PROVIDER', 'RECOVERY_FROM_CLIENT_OR_OTHER_SIDE',
     'Money recovered from client and/or other side (inc. stat charge)', TRUE, 30,
     '${amendment_reference_seed_actor}', now()),
    -- Contract Management
    ('01890a5d-ac96-774b-9c00-000000000201', 'CONTRACT_MANAGEMENT', 'INCORRECT_MEANS_ASSESSMENT',
     'Incorrect Means Assessment', TRUE, 10, '${amendment_reference_seed_actor}', now()),
    ('01890a5d-ac96-774b-9c00-000000000202', 'CONTRACT_MANAGEMENT', 'OTHER',
     'Other', TRUE, 20, '${amendment_reference_seed_actor}', now()),
    -- Assurance
    ('01890a5d-ac96-774b-9c00-000000000301', 'ASSURANCE', 'INCORRECT_MEANS_ASSESSMENT',
     'Incorrect Means Assessment', TRUE, 10, '${amendment_reference_seed_actor}', now()),
    ('01890a5d-ac96-774b-9c00-000000000302', 'ASSURANCE', 'OTHER',
     'Other', TRUE, 20, '${amendment_reference_seed_actor}', now());


