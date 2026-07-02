-- Add nullable message_code column to validation_message_log table
-- Stores the message code from FSP payloads when source is FSP and type is ERROR or WARNING
ALTER TABLE claims.validation_message_log
    ADD COLUMN message_code VARCHAR(50) NULL;