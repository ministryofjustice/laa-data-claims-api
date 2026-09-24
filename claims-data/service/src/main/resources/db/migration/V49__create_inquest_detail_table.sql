CREATE TABLE claims.inquest_detail (
    id UUID NOT NULL,
    claim_id UUID NOT NULL,
    deceased_forename VARCHAR(30),
    deceased_surname VARCHAR(30),
    deceased_date_of_death DATE,
    coroners_inquest_reference VARCHAR(30),
    created_by_user_id TEXT NOT NULL,
    created_on TIMESTAMPTZ NOT NULL,
    updated_by_user_id TEXT,
    updated_on TIMESTAMPTZ,

    CONSTRAINT pk_inquest_detail PRIMARY KEY (id),
    CONSTRAINT uq_inquest_detail_claim_id UNIQUE (claim_id),
    CONSTRAINT fk_inquest_detail_claim_id FOREIGN KEY (claim_id) REFERENCES claims.claim (id)
);
