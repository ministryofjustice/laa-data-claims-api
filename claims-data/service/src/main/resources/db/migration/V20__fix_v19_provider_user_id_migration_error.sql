-- V20: Safely handle provider_user_id column addition
-- This migration only runs if V19 succeeded but we need to ensure the column is properly configured
-- If V19 failed, the application configuration will handle the repair

-- Only proceed if the column exists (meaning V19 at least partially succeeded)
DO $$
BEGIN
    -- Check if provider_user_id column exists
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'claims' 
        AND table_name = 'submission' 
        AND column_name = 'provider_user_id'
    ) THEN
        -- Column exists, ensure it's properly populated and configured
        
        -- Make it nullable first if it's NOT NULL (in case V19 partially succeeded)
        BEGIN
            ALTER TABLE submission ALTER COLUMN provider_user_id DROP NOT NULL;
        EXCEPTION
            WHEN OTHERS THEN
                -- Column might already be nullable, continue
                NULL;
        END;
        
        -- Populate any missing values
        UPDATE submission s
        SET provider_user_id = (
            SELECT bs.created_by_user_id
            FROM bulk_submission bs
            WHERE bs.id = s.bulk_submission_id
        )
        WHERE provider_user_id IS NULL;
        
        -- Now make it NOT NULL
        ALTER TABLE submission ALTER COLUMN provider_user_id SET NOT NULL;
        
        RAISE NOTICE 'V20: Successfully configured provider_user_id column';
    ELSE
        -- Column doesn't exist, V19 completely failed
        -- Add the column properly
        ALTER TABLE submission ADD COLUMN provider_user_id TEXT;
        
        -- Populate it
        UPDATE submission s
        SET provider_user_id = (
            SELECT bs.created_by_user_id
            FROM bulk_submission bs
            WHERE bs.id = s.bulk_submission_id
        );
        
        -- Make it NOT NULL
        ALTER TABLE submission ALTER COLUMN provider_user_id SET NOT NULL;
        
        RAISE NOTICE 'V20: Added and configured provider_user_id column';
    END IF;
END $$;
