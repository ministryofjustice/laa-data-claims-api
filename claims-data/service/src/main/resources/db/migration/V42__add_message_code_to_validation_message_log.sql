-- Add nullable message_code column to validation_message_log table
-- Stores message codes from validation payloads (e.g. FSP) when provided
ALTER TABLE claims.validation_message_log
    ADD COLUMN message_code VARCHAR(20) NULL;