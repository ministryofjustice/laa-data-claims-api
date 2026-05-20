-- ------------------------------------------------------------------
-- Seed Data for Amendment Reference Tables
-- ------------------------------------------------------------------
-- ------------------------------------------------------------------
-- Seed Data for Amendment Reference Tables (Aligned with your V39 RTF)
-- ------------------------------------------------------------------

-- 1. Populate Requested By Types first, using Postgres 18 native uuidv7()
-- We use a CTE (Common Table Expression) to temporarily store the generated UUIDs so we can reference them below
WITH inserted_requesters AS (
INSERT INTO claims.requested_by_reference (id, code, display_label, is_active, display_order)
VALUES
    (uuidv7(), 'PROVIDER', 'Provider', true, 10),
    (uuidv7(), 'AUDITOR', 'Auditor', true, 20),
    (uuidv7(), 'CONTRACT_MANAGEMENT', 'Contract Management', true, 30),
    (uuidv7(), 'ASSURANCE', 'Assurance', true, 40)
ON CONFLICT (code) DO UPDATE
                          SET display_label = EXCLUDED.display_label, is_active = EXCLUDED.is_active
                          RETURNING id, code
                          )

-- 2. Populate Amendment Reasons using the actual UUIDs from the step above
                      INSERT INTO claims.amendment_reason_reference (id, requested_by_reference_id, code, display_label, is_active, display_order)
                      VALUES
                          -- Reasons valid for PROVIDER
                          (uuidv7(), (SELECT id FROM inserted_requesters WHERE code = 'PROVIDER'), 'TYPING_ERROR', 'Typing Error', true, 10),
                          (uuidv7(), (SELECT id FROM inserted_requesters WHERE code = 'PROVIDER'), 'OMISSION', 'Omitted Information', true, 20),

                          -- Reasons valid for AUDITOR
                          (uuidv7(), (SELECT id FROM inserted_requesters WHERE code = 'AUDITOR'), 'COMPLIANCE_CORRECTION', 'Compliance Correction', true, 10),
                          (uuidv7(), (SELECT id FROM inserted_requesters WHERE code = 'AUDITOR'), 'ROUTINE_AUDIT', 'Routine Audit Adjustment', true, 20),

                          -- Reasons valid for CONTRACT_MANAGEMENT
                          (uuidv7(), (SELECT id FROM inserted_requesters WHERE code = 'CONTRACT_MANAGEMENT'), 'SCHEDULE_AMENDMENT', 'Schedule Amendment Change', true, 10)
                      ON CONFLICT (requested_by_reference_id, code) DO UPDATE  -- Matches your actual unique constraint
                                                                           SET display_label = EXCLUDED.display_label, is_active = EXCLUDED.is_active;
