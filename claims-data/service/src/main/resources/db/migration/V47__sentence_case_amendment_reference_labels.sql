-- Change the amendment_requested_by reference data display labels from title case to sentence case (DSTEW-1594 follow-up).

UPDATE requested_by_reference
SET display_label = 'Contract management'
WHERE code = 'CONTRACT_MANAGEMENT';

UPDATE amendment_reason_reference
SET display_label = 'Provider error'
WHERE requested_by_code = 'PROVIDER' AND code = 'PROVIDER_ERROR';

UPDATE amendment_reason_reference
SET display_label = 'Incorrect means assessment'
WHERE code = 'INCORRECT_MEANS_ASSESSMENT';
