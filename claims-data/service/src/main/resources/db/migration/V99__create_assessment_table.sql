CREATE TABLE assessment (
    id                                                  UUID NOT NULL,
    claim_id                                            UUID NOT NULL,
    claim_summary_fee_id                                UUID NOT NULL,
    assessment_outcome                                  TEXT NOT NULL,
    fixed_fee_amount                                    NUMERIC,
    net_travel_costs_amount                             NUMERIC,
    net_waiting_costs_amount                            NUMERIC,
    net_profit_costs_amount                             NUMERIC,
    disbursement_amount                                 NUMERIC,
    disbursement_vat_amount                             NUMERIC,
    net_cost_of_counsel_amount                          NUMERIC,
    detention_travel_and_waiting_costs_amount           NUMERIC,
    is_vat_applicable                                   BOOLEAN,
    bolt_on_adjourned_hearing_fee                       NUMERIC,
    jr_form_filling_amount                              NUMERIC,
    bolt_on_cmrh_oral_fee                               NUMERIC,
    bolt_on_cmrh_telephone_fee                          NUMERIC,
    bolt_on_substantive_hearing_fee                     NUMERIC,
    bolt_on_home_office_interview_fee                   NUMERIC,
    assessed_total_vat                                  NUMERIC NOT NULL,
    assessed_total_incl_vat                             NUMERIC NOT NULL,
    allowed_total_vat                                   NUMERIC NOT NULL,
    allowed_total_incl_vat                              NUMERIC NOT NULL,
    created_by_user_id                                  TEXT NOT NULL,
    created_on                                          TIMESTAMPTZ NOT NULL,
    updated_by_user_id                                  TEXT NOT NULL,
    updated_on                                          TIMESTAMPTZ,

    CONSTRAINT pk_assessment PRIMARY KEY (id),
    CONSTRAINT fk_claim_id FOREIGN KEY (claim_id) REFERENCES claim(id),
    CONSTRAINT fk_claim_summary_fee_id FOREIGN KEY (claim_summary_fee_id) REFERENCES claim_summary_fee(id),
    CONSTRAINT chk_assessment_outcome CHECK (assessment_outcome IN ('PAID_IN_FULL', 'REDUCED_STILL_ESCAPED', 'REDUCED_TO_FIXED_FEE', 'NILLED'))
);

-- CREATE INDEX ix_claim_id ON claim(id);
CREATE INDEX ix_claim_id ON assessment(claim_id);
-- CREATE INDEX ix_claim_summary_fee_id ON claim_summary_fee(id);