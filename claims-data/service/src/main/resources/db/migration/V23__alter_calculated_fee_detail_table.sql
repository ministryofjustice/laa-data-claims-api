/*Rename column to detention_travel_and_waiting_costs_amount*/
ALTER TABLE claims.calculated_fee_detail
    RENAME COLUMN detention_and_waiting_costs_amount
        TO detention_travel_and_waiting_costs_amount;

/*Add bolt on substantive hearing count and fee*/
ALTER TABLE claims.calculated_fee_detail
    ADD COLUMN bolt_on_substantive_hearing_count INTEGER,
    ADD COLUMN bolt_on_substantive_hearing_fee NUMERIC;