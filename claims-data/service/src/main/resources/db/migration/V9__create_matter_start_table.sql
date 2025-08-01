CREATE TABLE matter_start (
   id                        UUID NOT NULL,
   submission_id             UUID NOT NULL,
   schedule_reference        TEXT,
   category_code             TEXT,
   procurement_area_code     TEXT,
   access_point_code         TEXT,
   delivery_location         TEXT,
   number_of_matter_starts   INTEGER,
   created_by_user_id        TEXT NOT NULL,
   created_on                TIMESTAMPTZ NOT NULL,
   updated_by_user_id        TEXT,
   updated_on                TIMESTAMPTZ,

   CONSTRAINT pk_matter_start PRIMARY KEY (id),
   CONSTRAINT fk_matter_start_submission_id FOREIGN KEY (submission_id) REFERENCES submission(id)
);
CREATE INDEX ix_matter_start_submission_id ON matter_start(submission_id);