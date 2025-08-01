CREATE TABLE calculated_fee_detail (
   id                       UUID NOT NULL,
   claim_summary_fee_id     UUID NOT NULL,
   claim_id                 UUID NOT NULL,
   fee_code                 TEXT,
   fee_type                 TEXT,
   total_fee                NUMERIC,
   vat_amount               NUMERIC,
   calculated_fee_status    TEXT,
   created_by_user_id       TEXT NOT NULL,
   created_on               TIMESTAMPTZ NOT NULL,
   updated_by_user_id       TEXT,
   updated_on               TIMESTAMPTZ,

   CONSTRAINT pk_calculated_fee_detail PRIMARY KEY (id),
   CONSTRAINT fk_calculated_fee_detail_claim_summary_fee_id FOREIGN KEY (claim_summary_fee_id) REFERENCES claim_summary_fee(id),
   CONSTRAINT fk_calculated_fee_detail_claim_id FOREIGN KEY (claim_id) REFERENCES claim(id)
);
CREATE INDEX ix_calculated_fee_detail_claim_summary_fee_id ON calculated_fee_detail(claim_summary_fee_id);
CREATE INDEX ix_calculated_fee_detail_claim_id ON calculated_fee_detail(claim_id);