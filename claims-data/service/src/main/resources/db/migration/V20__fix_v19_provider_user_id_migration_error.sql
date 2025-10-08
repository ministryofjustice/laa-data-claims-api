-- V20: Fix the error from V19 migration
-- V19 failed because it tried to add a NOT NULL column to a table with existing data
-- This migration cleans up and properly adds the provider_user_id column

-- Step 1: Remove the failed V19 migration from flyway history
-- This allows migrations to continue past the failure
DELETE FROM flyway_schema_history WHERE version = '19';

-- Step 2: Check if column exists and handle appropriately
-- Only add the column if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'claims' 
        AND table_name = 'submission' 
        AND column_name = 'provider_user_id'
    ) THEN
        -- Column doesn't exist, safe to add it
        ALTER TABLE submission ADD COLUMN provider_user_id TEXT;
    ELSE
        -- Column exists but might be NOT NULL (causing the original error)
        -- Make it nullable first so we can populate it safely
        ALTER TABLE submission ALTER COLUMN provider_user_id DROP NOT NULL;
    END IF;
END $$;

-- Step 4: Populate the column with data from bulk_submission
UPDATE submission s
SET provider_user_id = (
    SELECT bs.created_by_user_id
    FROM bulk_submission bs
    WHERE bs.id = s.bulk_submission_id
);

-- Step 5: Handle any rows where bulk_submission_id doesn't match
UPDATE submission 
SET provider_user_id = 'unknown_user' 
WHERE provider_user_id IS NULL;

-- Step 6: Now make the column NOT NULL (safe because all rows have values)
ALTER TABLE submission ALTER COLUMN provider_user_id SET NOT NULL;
