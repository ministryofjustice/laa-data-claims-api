CREATE TABLE claims.submission_metadata (
    id UUID PRIMARY KEY,
    bulk_submission_id UUID UNIQUE
        REFERENCES claims.bulk_submission (id) ON DELETE CASCADE,
    submission_id UUID UNIQUE
        REFERENCES claims.submission (id) ON DELETE CASCADE,
    spreadsheet_version TEXT,
    spreadsheet_platform TEXT,
    source_file_format TEXT,
    CONSTRAINT chk_submission_metadata_spreadsheet_version
        CHECK (spreadsheet_version ~ '^[0-9]{1,2}\.[0-9]{2}$'),
    CONSTRAINT chk_submission_metadata_spreadsheet_platform
        CHECK (spreadsheet_platform IN ('WINDOWS', 'MAC', 'OTHER')),
    CONSTRAINT chk_submission_metadata_source_file_format
        CHECK (source_file_format IN ('CSV', 'TXT', 'XML', 'ONLINE'))
);

COMMENT ON COLUMN claims.submission_metadata.spreadsheet_version
    IS 'Spreadsheet version declared in the uploaded file; NULL means undeclared.';

COMMENT ON COLUMN claims.submission_metadata.spreadsheet_platform
    IS 'Spreadsheet platform declared in the uploaded file; NULL means undeclared.';

COMMENT ON COLUMN claims.submission_metadata.source_file_format
    IS 'CSV, TXT or XML for file uploads; ONLINE for fileless submissions; NULL means undeclared.';
