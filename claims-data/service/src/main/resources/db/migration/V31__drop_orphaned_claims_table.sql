-- V31__drop_orphaned_claims_table.sql
-- Drop the orphaned 'claims' table that was created in the now-deleted V10 migration.
-- This table was originally created as a temporary fix for integration tests and was
-- intended to be removed when the /api/v1/claims endpoint was removed.
-- The migration file was deleted but no DROP migration was created, leaving the table
-- orphaned in existing environments (uat, staging, production).

DROP TABLE IF EXISTS claims;
