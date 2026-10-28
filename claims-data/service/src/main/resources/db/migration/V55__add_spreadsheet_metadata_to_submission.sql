CREATE TYPE claims.spreadsheet_platform AS ENUM ('WINDOWS', 'MAC', 'OTHER');

CREATE TYPE claims.source_file_format AS ENUM ('CSV', 'TXT', 'XML');

ALTER TABLE claims.submission
    ADD COLUMN spreadsheet_version TEXT,
    ADD COLUMN spreadsheet_platform claims.spreadsheet_platform,
    ADD COLUMN source_file_format claims.source_file_format;

COMMENT ON COLUMN claims.submission.spreadsheet_version
    IS 'Spreadsheet version declared in the uploaded file; NULL means undeclared.';

COMMENT ON COLUMN claims.submission.spreadsheet_platform
    IS 'Spreadsheet platform declared in the uploaded file; NULL means undeclared.';

COMMENT ON COLUMN claims.submission.source_file_format
    IS 'Source file format resolved at upload; NULL means undeclared.';
