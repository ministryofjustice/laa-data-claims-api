ALTER TABLE calculated_fee_detail
DROP COLUMN total_fee;
ALTER TABLE calculated_fee_detail
DROP COLUMN vat_amount;
ALTER TABLE calculated_fee_detail
DROP COLUMN calculated_fee_status;

ALTER TABLE calculated_fee_detail
ADD COLUMN fee_code_description TEXT,
ADD COLUMN category_of_law TEXT,
ADD COLUMN total_amount NUMERIC,
ADD COLUMN vat_indicator BOOLEAN,
ADD COLUMN vat_rate_applied NUMERIC,
ADD COLUMN calculated_vat_amount NUMERIC,
ADD COLUMN disbursement_amount NUMERIC,
ADD COLUMN requested_net_disbursement_amount NUMERIC,
ADD COLUMN disbursement_vat_amount NUMERIC,
ADD COLUMN hourly_total_amount NUMERIC,
ADD COLUMN fixed_fee_amount NUMERIC,
ADD COLUMN net_profit_costs_amount NUMERIC,
ADD COLUMN requested_net_profit_costs_amount NUMERIC,
ADD COLUMN net_cost_of_counsel_amount NUMERIC,
ADD COLUMN net_travel_costs_amount NUMERIC,
ADD COLUMN net_waiting_costs_amount NUMERIC,
ADD COLUMN detention_and_waiting_costs_amount NUMERIC,
ADD COLUMN jr_form_filling_amount NUMERIC,
ADD COLUMN travel_and_waiting_costs_amount NUMERIC,
ADD COLUMN bolt_on_total_fee_amount NUMERIC,
ADD COLUMN bolt_on_adjourned_hearing_count INTEGER,
ADD COLUMN bolt_on_adjourned_hearing_fee NUMERIC,
ADD COLUMN bolt_on_cmrh_telephone_count INTEGER,
ADD COLUMN bolt_on_cmrh_telephone_fee NUMERIC,
ADD COLUMN bolt_on_cmrh_oral_count INTEGER,
ADD COLUMN bolt_on_cmrh_oral_fee NUMERIC,
ADD COLUMN bolt_on_home_office_interview_count INTEGER,
ADD COLUMN bolt_on_home_office_interview_fee NUMERIC,
ADD COLUMN escape_case_flag BOOLEAN,
ADD COLUMN scheme_id TEXT;