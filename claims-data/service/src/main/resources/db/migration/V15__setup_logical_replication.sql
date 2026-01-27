-- Create reporting user if it doesn't exist (roles are cluster-wide in RDS)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${reporting_username}') THEN
        EXECUTE 'CREATE USER ${reporting_username} WITH PASSWORD ''${reporting_password}''';
    END IF;
END$$;

-- Grant rds_replication role if it exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rds_replication') THEN
        EXECUTE 'GRANT rds_replication TO ${reporting_username}';
    END IF;
END$$;

GRANT USAGE ON SCHEMA claims TO ${reporting_username};

GRANT SELECT ON ALL TABLES IN SCHEMA claims TO ${reporting_username};

ALTER DEFAULT PRIVILEGES IN SCHEMA claims GRANT SELECT ON TABLES TO ${reporting_username};

-- Create publication if it doesn't exist (publications are cluster-wide in RDS)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'claims_reporting_service_pub') THEN
        CREATE PUBLICATION claims_reporting_service_pub
            FOR TABLE claims.submission,
                      claims.claim,
                      claims.client,
                      claims.claim_case,
                      claims.claim_summary_fee,
                      claims.calculated_fee_detail;
    END IF;
END$$;

