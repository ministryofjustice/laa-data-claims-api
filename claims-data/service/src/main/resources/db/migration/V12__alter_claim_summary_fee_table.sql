ALTER TABLE claim_summary_fee
    ALTER COLUMN ho_interview TYPE INTEGER
          USING ho_interview::integer;

ALTER TABLE claim_summary_fee
    ALTER COLUMN adjourned_hearing_fee_amount TYPE INTEGER
          USING ROUND(adjourned_hearing_fee_amount)::integer;
