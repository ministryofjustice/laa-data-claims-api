CREATE TABLE calculated_fee_detail (
   id                       UUID PRIMARY KEY ,
   case_summary_fee_id      UUID NOT NULL REFERENCES claim_summary_fee(id),
   claim_id                 UUID NOT NULL REFERENCES claim(id),
   fee_code                 TEXT,
   fee_type                 TEXT,
   total_fee                NUMERIC,
   vat_amount               NUMERIC,
   calculated_fee_status    TEXT,
   created_by_user_id       TEXT NOT NULL,
   created_on               TIMESTAMPTZ NOT NULL,
   updated_by_user_id       TEXT,
   updated_on               TIMESTAMPTZ
);
CREATE INDEX idx_calculated_fee_detail_case_summary_fee_id ON calculated_fee_detail(case_summary_fee_id);
CREATE INDEX idx_calculated_fee_detail_claim_id ON calculated_fee_detail(claim_id);