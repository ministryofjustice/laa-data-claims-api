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

