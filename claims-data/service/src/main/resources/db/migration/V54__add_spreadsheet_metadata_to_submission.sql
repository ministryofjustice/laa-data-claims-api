ALTER TABLE claims.submission
    ADD COLUMN spreadsheet_version TEXT,
    ADD COLUMN spreadsheet_platform TEXT,
    ADD COLUMN source_file_format TEXT;

COMMENT ON COLUMN claims.submission.spreadsheet_version
    IS 'Spreadsheet version declared in the uploaded file; NULL means undeclared.';

COMMENT ON COLUMN claims.submission.spreadsheet_platform
    IS 'Spreadsheet platform declared in the uploaded file; NULL means undeclared.';

COMMENT ON COLUMN claims.submission.source_file_format
    IS 'Source file format resolved at upload; NULL means undeclared.';
