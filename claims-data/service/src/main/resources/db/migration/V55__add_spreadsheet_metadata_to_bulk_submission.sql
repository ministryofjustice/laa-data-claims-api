ALTER TABLE claims.bulk_submission
    ADD COLUMN spreadsheet_version TEXT,
    ADD COLUMN spreadsheet_platform TEXT,
    ADD COLUMN source_file_format TEXT,
    ADD CONSTRAINT chk_bulk_submission_spreadsheet_version
        CHECK (spreadsheet_version ~ '^[0-9]{1,2}\.[0-9]{2}$'),
    ADD CONSTRAINT chk_bulk_submission_spreadsheet_platform
        CHECK (spreadsheet_platform IN ('WINDOWS', 'MAC', 'OTHER')),
    ADD CONSTRAINT chk_bulk_submission_source_file_format
        CHECK (source_file_format IN ('CSV', 'TXT', 'XML'));

COMMENT ON COLUMN claims.bulk_submission.spreadsheet_version
    IS 'Spreadsheet version declared in the uploaded file; NULL means undeclared.';

COMMENT ON COLUMN claims.bulk_submission.spreadsheet_platform
    IS 'Spreadsheet platform declared in the uploaded file; NULL means undeclared.';

COMMENT ON COLUMN claims.bulk_submission.source_file_format
    IS 'Source file format resolved at upload; NULL means undeclared.';
