-- V20260330__claim_amendment_audit_trigger.sql
-- Create a dedicated audit function and trigger for claim_amendment using claim_id as the primary key

CREATE OR REPLACE FUNCTION audit.audit_claim_amendment_changes()
RETURNS trigger AS $$
DECLARE
    v_actor_user TEXT;
    v_actor_service TEXT;
    v_pk TEXT;
    v_old_data JSONB;
    v_new_data JSONB;
BEGIN
    -- Set actor_service from session or fallback to current_user
    v_actor_service := COALESCE(current_setting('audit.actor_service', true), current_user);

    -- Set actor_user and use claim_id as the primary key
    IF TG_OP = 'INSERT' THEN
        v_actor_user := NEW.created_by_user_id;
        v_pk := NEW.claim_id::text;
        v_old_data := NULL;
        v_new_data := to_jsonb(NEW);
    ELSIF TG_OP = 'DELETE' THEN
        v_actor_user := OLD.updated_by_user_id;
        v_pk := OLD.claim_id::text;
        v_old_data := to_jsonb(OLD);
        v_new_data := NULL;
    ELSE -- UPDATE
        v_actor_user := NEW.updated_by_user_id;
        v_pk := NEW.claim_id::text;
        v_old_data := to_jsonb(OLD);
        v_new_data := to_jsonb(NEW);
    END IF;

    INSERT INTO audit.audit_log (
        table_name, operation, primary_key, old_data, new_data, changed_at, actor_user, actor_service
    ) VALUES (
        TG_TABLE_NAME, TG_OP, v_pk, v_old_data, v_new_data, now(), v_actor_user, v_actor_service
    );
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_trigger WHERE tgname = 'trg_audit_claim_amendment_changes'
    ) THEN
        DROP TRIGGER trg_audit_claim_amendment_changes ON claims.claim_amendment;
    END IF;
END$$;

CREATE TRIGGER trg_audit_claim_amendment_changes
AFTER INSERT OR UPDATE OR DELETE ON claims.claim_amendment
FOR EACH ROW
EXECUTE FUNCTION audit.audit_claim_amendment_changes();

