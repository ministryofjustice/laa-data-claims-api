-- Migration: enable pg_trgm and add a trigram (GIN) index to speed up
-- case-insensitive substring searches (e.g. ILIKE/LOWER(...) LIKE '%term%')
--
-- This creates the pg_trgm extension if it does not already exist and
-- adds an index on lower(case_reference_number) using the gin_trgm_ops
-- operator class so queries that use LOWER(case_reference_number) LIKE
-- '%...%' can use the index.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS ix_claim_case_reference_number_trgm ON claim USING gin (lower(case_reference_number) gin_trgm_ops);