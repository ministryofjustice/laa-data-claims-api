CREATE TABLE calculated_fee_details (
   id                       UUID PRIMARY KEY ,
   case_summary_fee_id      UUID NOT NULL REFERENCES claim_summary_fees(id),
   claim_id                 UUID NOT NULL REFERENCES claims(id),
   fee_code                 TEXT,
   fee_type                 TEXT,
   total_fee                NUMERIC,
   vat_amount               NUMERIC,
   calculated_fee_status    TEXT,
   created_by_user_id       UUID NOT NULL,
   created_on               TIMESTAMPTZ NOT NULL,
   modified_by_user_id      UUID,
   modified_on              TIMESTAMPTZ
);
CREATE INDEX idx_calculated_fee_details_case_summary_fee_id ON calculated_fee_details(case_summary_fee_id);
CREATE INDEX idx_calculated_fee_details_claim_id ON calculated_fee_details(claim_id);