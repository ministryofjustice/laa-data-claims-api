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
