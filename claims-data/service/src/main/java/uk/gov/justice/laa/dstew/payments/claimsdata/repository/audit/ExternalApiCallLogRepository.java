package uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit;

/**
 * Write-side access to {@code audit.external_api_call_log} table.
 *
 * <p>Append-only: rows are evidence and are never updated or deleted by the application.
 */
public interface ExternalApiCallLogRepository {
  /**
   * Insert one audit row in its own transaction. This is important because the audit table is in a
   * different schema and we want to avoid locking issues.
   *
   * @param entry the call to record
   */
  void insert(ExternalApiCallLogEntry entry);
}
