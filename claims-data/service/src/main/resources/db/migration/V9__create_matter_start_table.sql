CREATE TABLE matter_start (
   id                        UUID PRIMARY KEY,
   submission_id             UUID NOT NULL REFERENCES submission(id),
   schedule_reference        TEXT,
   category_code             TEXT,
   procurement_area_code     TEXT,
   access_point_code         TEXT,
   delivery_location         TEXT,
   number_of_matter_starts   INTEGER,
   created_by_user_id        TEXT NOT NULL,
   created_on                TIMESTAMPTZ NOT NULL,
   updated_by_user_id        TEXT,
   updated_on                TIMESTAMPTZ
);
CREATE INDEX idx_matter_start_submission_id ON matter_start(submission_id);