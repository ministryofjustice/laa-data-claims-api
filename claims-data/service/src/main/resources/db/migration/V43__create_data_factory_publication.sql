--Sets up the data factory replication user and creates the claims_data_factory_service_pub publication.
--Publishes all tables in the claims schema except validation_message_log and flyway_schema_history.
--
--Idempotent: existence checks allow safe re-runs and are required in preview environments where
--multiple databases share a single RDS instance (users and roles are global to the instance).

-- 1. Instance-level user setup
--    Guarded because users are global: creating a user that already exists raises an error.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${data_factory_username}') THEN
        EXECUTE format('CREATE USER %I WITH PASSWORD %L', '${data_factory_username}', '${data_factory_password}');
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rds_replication') THEN
            EXECUTE format('GRANT rds_replication TO %I', '${data_factory_username}');
        END IF;
    END IF;
END$$;

-- 2. Database-level access grants (idempotent)
GRANT USAGE ON SCHEMA claims TO ${data_factory_username};
GRANT SELECT ON ALL TABLES IN SCHEMA claims TO ${data_factory_username};
ALTER DEFAULT PRIVILEGES IN SCHEMA claims GRANT SELECT ON TABLES TO ${data_factory_username};

-- 3. Publication
--    Guarded via pg_publication: 'CREATE PUBLICATION IF NOT EXISTS' requires PG15+.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'claims_data_factory_service_pub') THEN
        CREATE PUBLICATION claims_data_factory_service_pub
            FOR TABLE claims.bulk_submission,
                      claims.submission,
                      claims.claim,
                      claims.client,
                      claims.claim_case,
                      claims.claim_summary_fee,
                      claims.calculated_fee_detail,
                      claims.matter_start,
                      claims.replication_summary,
                      claims.assessment;
    END IF;
END$$;
