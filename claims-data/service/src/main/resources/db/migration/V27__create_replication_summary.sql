CREATE TABLE replication_summary (
  table_name text NOT NULL,
  summary_date date NOT NULL,
  record_count bigint NOT NULL,
  updated_count bigint DEFAULT 0,
  wal_lsn pg_lsn DEFAULT pg_current_wal_lsn(),
  created_on timestamptz DEFAULT now(),
  CONSTRAINT pk_replication_summary PRIMARY KEY (table_name, summary_date)
);

ALTER PUBLICATION claims_reporting_service_pub ADD TABLE claims.replication_summary;
