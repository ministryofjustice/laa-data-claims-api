/*Remove substantive hearing count*/
ALTER TABLE calculated_fee_detail
    DROP COLUMN bolt_on_substantive_hearing_count;