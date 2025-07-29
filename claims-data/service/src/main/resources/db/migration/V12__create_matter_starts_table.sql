CREATE TABLE matter_starts (
   id                        UUID PRIMARY KEY,
   submission_id             UUID NOT NULL REFERENCES submissions(id),
   schedule_reference        TEXT,
   category_code             TEXT,
   procurement_area_code     TEXT,
   access_point_code         TEXT,
   delivery_location         TEXT,
   number_of_matter_starts   INTEGER,
   created_by_user_id        UUID NOT NULL,
   created_on                TIMESTAMPTZ NOT NULL,
   modified_by_user_id       UUID,
   modified_on               TIMESTAMPTZ
);
CREATE INDEX idx_matter_starts_submission_id ON matter_starts(submission_id);