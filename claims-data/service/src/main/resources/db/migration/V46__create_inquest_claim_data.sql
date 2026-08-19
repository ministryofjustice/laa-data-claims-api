CREATE TABLE inquest_detail (
    id UUID PRIMARY KEY,
    claim_id UUID NOT NULL UNIQUE REFERENCES claim(id),
    deceased_forename VARCHAR(255),
    deceased_surname VARCHAR(255),
    deceased_date_of_birth DATE,
    deceased_date_of_death DATE,
    coroners_inquest_reference VARCHAR(255),
    created_by_user_id VARCHAR(255) NOT NULL,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by_user_id VARCHAR(255),
    updated_on TIMESTAMP WITH TIME ZONE
);

CREATE TABLE department_reference (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    display_label VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL,
    display_order INTEGER NOT NULL UNIQUE,
    created_by_user_id VARCHAR(255) NOT NULL,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by_user_id VARCHAR(255),
    updated_on TIMESTAMP WITH TIME ZONE
);

CREATE TABLE claim_interested_department (
    id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claim(id) ON DELETE CASCADE,
    department_code VARCHAR(80) NOT NULL REFERENCES department_reference(code),
    created_by_user_id VARCHAR(255) NOT NULL,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by_user_id VARCHAR(255),
    updated_on TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_claim_interested_department UNIQUE (claim_id, department_code)
);

CREATE TABLE claim_interested_public_authority (
    id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claim(id) ON DELETE CASCADE,
    authority_name VARCHAR(255) NOT NULL,
    display_order INTEGER NOT NULL,
    created_by_user_id VARCHAR(255) NOT NULL,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by_user_id VARCHAR(255),
    updated_on TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_claim_public_authority_order UNIQUE (claim_id, display_order)
);

-- Public authorities deliberately remain free text. A future governed list can be added
-- additively by creating public_authority_reference and adding an authority_code FK here,
-- then migrating matched authority_name values without changing the claim-level model.

INSERT INTO department_reference
    (id, code, display_label, is_active, display_order, created_by_user_id)
VALUES
    ('01960000-0000-7000-8000-000000000001', 'AGO', 'Attorney General''s Office', TRUE, 1, 'flyway'),
    ('01960000-0000-7000-8000-000000000002', 'CO', 'Cabinet Office', TRUE, 2, 'flyway'),
    ('01960000-0000-7000-8000-000000000003', 'DBT', 'Department for Business and Trade', TRUE, 3, 'flyway'),
    ('01960000-0000-7000-8000-000000000004', 'DCMS', 'Department for Culture, Media and Sport', TRUE, 4, 'flyway'),
    ('01960000-0000-7000-8000-000000000005', 'DESNZ', 'Department for Energy Security and Net Zero', TRUE, 5, 'flyway'),
    ('01960000-0000-7000-8000-000000000006', 'DEFRA', 'Department for Environment, Food and Rural Affairs', TRUE, 6, 'flyway'),
    ('01960000-0000-7000-8000-000000000007', 'DfE', 'Department for Education', TRUE, 7, 'flyway'),
    ('01960000-0000-7000-8000-000000000008', 'OLL', 'Office of the Leader of the House of Lords', TRUE, 8, 'flyway'),
    ('01960000-0000-7000-8000-000000000009', 'DSIT', 'Department for Science, Innovation and Technology', TRUE, 9, 'flyway'),
    ('01960000-0000-7000-8000-000000000010', 'DfT', 'Department for Transport', TRUE, 10, 'flyway'),
    ('01960000-0000-7000-8000-000000000011', 'DWP', 'Department for Work and Pensions', TRUE, 11, 'flyway'),
    ('01960000-0000-7000-8000-000000000012', 'DHSC', 'Department of Health and Social Care', TRUE, 12, 'flyway'),
    ('01960000-0000-7000-8000-000000000013', 'FCDO', 'Foreign, Commonwealth & Development Office', TRUE, 13, 'flyway'),
    ('01960000-0000-7000-8000-000000000014', 'HMT', 'HM Treasury', TRUE, 14, 'flyway'),
    ('01960000-0000-7000-8000-000000000015', 'HO', 'Home Office', TRUE, 15, 'flyway'),
    ('01960000-0000-7000-8000-000000000016', 'MOD', 'Ministry of Defence', TRUE, 16, 'flyway'),
    ('01960000-0000-7000-8000-000000000017', 'MHCLG', 'Ministry of Housing, Communities and Local Government', TRUE, 17, 'flyway'),
    ('01960000-0000-7000-8000-000000000018', 'MOJ', 'Ministry of Justice', TRUE, 18, 'flyway'),
    ('01960000-0000-7000-8000-000000000019', 'NIO', 'Northern Ireland Office', TRUE, 19, 'flyway'),
    ('01960000-0000-7000-8000-000000000020', 'OAG', 'Office of the Advocate General for Scotland', TRUE, 20, 'flyway'),
    ('01960000-0000-7000-8000-000000000021', 'OLC', 'Office of the Leader of the House of Commons', TRUE, 21, 'flyway'),
    ('01960000-0000-7000-8000-000000000022', 'SO', 'Scotland Office', TRUE, 22, 'flyway'),
    ('01960000-0000-7000-8000-000000000023', 'UKEF', 'UK Export Finance', TRUE, 23, 'flyway'),
    ('01960000-0000-7000-8000-000000000024', 'WO', 'Wales Office', TRUE, 24, 'flyway');
