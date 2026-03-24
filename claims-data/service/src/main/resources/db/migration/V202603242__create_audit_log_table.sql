-- V20260324__create_audit_schema.sql
-- Create audit schema if it does not exist
CREATE SCHEMA IF NOT EXISTS audit;

-- V20260324__create_audit_log_table.sql
-- Create the audit_log table in the audit schema
DROP TABLE IF EXISTS audit.audit_log;
CREATE TABLE IF NOT EXISTS audit.audit_log (
    id BIGSERIAL PRIMARY KEY,
    table_name TEXT NOT NULL,
    operation TEXT NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    primary_key TEXT NOT NULL,
    old_data JSONB,
    new_data JSONB,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_user TEXT,
    actor_service TEXT NOT NULL,
    metadata JSONB
);

-- Enforce immutability: prevent UPDATE/DELETE
CREATE OR REPLACE FUNCTION audit.prevent_audit_log_modification()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit.audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS audit_log_no_update ON audit.audit_log;
CREATE TRIGGER audit_log_no_update
BEFORE UPDATE OR DELETE ON audit.audit_log
FOR EACH ROW EXECUTE FUNCTION audit.prevent_audit_log_modification();

-- V20260324__create_audit_trigger_function.sql
-- Create a trigger function to audit INSERT, UPDATE, DELETE on claim table
CREATE OR REPLACE FUNCTION audit.audit_claim_changes()
RETURNS trigger AS $$
DECLARE
v_actor_user TEXT;
    v_actor_service TEXT;
    v_pk TEXT;
    v_old_data JSONB;
    v_new_data JSONB;
BEGIN
    -- Set actor info
    v_actor_user := current_setting('audit.actor_user', true);
    v_actor_service := current_user;

    -- Set primary key (uses 'id' as PK for claim table, stored as text)
    IF TG_OP = 'DELETE' THEN
        v_pk := OLD.id::text;
        v_old_data := to_jsonb(OLD);
        v_new_data := NULL;
    ELSIF TG_OP = 'INSERT' THEN
        v_pk := NEW.id::text;
        v_old_data := NULL;
        v_new_data := to_jsonb(NEW);
    ELSE -- UPDATE
        v_pk := NEW.id::text;
        v_old_data := to_jsonb(OLD);
        v_new_data := to_jsonb(NEW);
    END IF;

    -- Optional: Redact sensitive fields here if needed
    -- Example: v_old_data := v_old_data - 'client_ssn';
    -- Example: v_new_data := v_new_data - 'client_ssn';

INSERT INTO audit.audit_log (
    table_name, operation, primary_key, old_data, new_data, changed_at, actor_user, actor_service
) VALUES (
             TG_TABLE_NAME, TG_OP, v_pk, v_old_data, v_new_data, now(), v_actor_user, v_actor_service
         );
RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- V20260324__create_audit_triggers_on_claim.sql
-- Add triggers for INSERT, UPDATE, DELETE on claim table
DROP TRIGGER IF EXISTS audit_claim_insert ON claim;
CREATE TRIGGER audit_claim_insert
    AFTER INSERT ON claim
    FOR EACH ROW EXECUTE FUNCTION audit.audit_claim_changes();

DROP TRIGGER IF EXISTS audit_claim_update ON claim;
CREATE TRIGGER audit_claim_update
    AFTER UPDATE ON claim
    FOR EACH ROW EXECUTE FUNCTION audit.audit_claim_changes();

DROP TRIGGER IF EXISTS audit_claim_delete ON claim;
CREATE TRIGGER audit_claim_delete
    AFTER DELETE ON claim
    FOR EACH ROW EXECUTE FUNCTION audit.audit_claim_changes();
