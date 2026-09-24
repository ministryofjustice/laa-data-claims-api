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
