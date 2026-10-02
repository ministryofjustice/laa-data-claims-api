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

CREATE TABLE claims.government_department_ref (
    id UUID NOT NULL,
    government_department_code VARCHAR(80) NOT NULL,
    display_label VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL,
    display_order INTEGER NOT NULL,
    created_by_user_id TEXT NOT NULL,
    created_on TIMESTAMPTZ NOT NULL,
    updated_by_user_id TEXT,
    updated_on TIMESTAMPTZ,

    CONSTRAINT pk_government_department_ref PRIMARY KEY (id),
    CONSTRAINT uq_government_department_ref_government_department_code UNIQUE (government_department_code)
);

CREATE TABLE claims.claim_interested_department (
    id UUID NOT NULL,
    claim_id UUID NOT NULL,
    government_department_id UUID NOT NULL,
    display_order INTEGER NOT NULL,
    created_by_user_id TEXT NOT NULL,
    created_on TIMESTAMPTZ NOT NULL,
    updated_by_user_id TEXT,
    updated_on TIMESTAMPTZ,

    CONSTRAINT pk_claim_interested_department PRIMARY KEY (id),
    CONSTRAINT fk_claim_interested_department_claim_id
        FOREIGN KEY (claim_id)
        REFERENCES claims.claim (id),
    CONSTRAINT fk_claim_interested_department_government_department_id
        FOREIGN KEY (government_department_id)
        REFERENCES claims.government_department_ref (id),
    CONSTRAINT uq_claim_interested_department_claim_id_display_order
        UNIQUE (claim_id, display_order)
);

ALTER TABLE claims.client
    ADD COLUMN is_means_tested BOOLEAN;

ALTER TABLE claims.calculated_fee_detail
    ADD COLUMN is_inquest BOOLEAN;
