CREATE USER ${reporting_username} WITH PASSWORD '${reporting_password}';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rds_replication') THEN
        EXECUTE 'GRANT rds_replication TO reporting_user';
END IF;
END$$;

GRANT USAGE ON SCHEMA claims TO ${reporting_username};

GRANT SELECT ON ALL TABLES IN SCHEMA claims TO ${reporting_username};

ALTER DEFAULT PRIVILEGES IN SCHEMA claims GRANT SELECT ON TABLES TO ${reporting_username};

--Create publications for all tables to be replicated
CREATE PUBLICATION claims_reporting_service_pub
    FOR TABLE claims.submission,
              claims.claim,
              claims.client,
              claims.claim_case,
              claims.claim_summary_fee,
              claims.calculated_fee_detail;

