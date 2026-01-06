package uk.gov.justice.laa.export.model;

import java.time.Instant;

/**
 * Audit event describing an export run.
 */
public final class ExportAuditEvent {
  private final String exportKey;
  private final boolean success;
  private final int maxRows;
  private final long rows;
  private final long durationMs;
  private final Instant startedAt;
  private final String error;

  private ExportAuditEvent(
      String exportKey,
      boolean success,
      int maxRows,
      long rows,
      long durationMs,
      Instant startedAt,
      String error) {
    this.exportKey = exportKey;
    this.success = success;
    this.maxRows = maxRows;
    this.rows = rows;
    this.durationMs = durationMs;
    this.startedAt = startedAt;
    this.error = error;
  }

  /**
   * Creates a success audit event.
   */
  public static ExportAuditEvent success(
      String exportKey, ValidatedExportRequest request, long rows, long startMs) {
    return new ExportAuditEvent(
        exportKey,
        true,
        request == null ? 0 : request.getMaxRows(),
        rows,
        System.currentTimeMillis() - startMs,
        Instant.ofEpochMilli(startMs),
        null);
  }

  /**
   * Creates a failure audit event.
   */
  public static ExportAuditEvent failure(
      String exportKey, ValidatedExportRequest request, long rows, long startMs, Exception e) {
    return new ExportAuditEvent(
        exportKey,
        false,
        request == null ? 0 : request.getMaxRows(),
        rows,
        System.currentTimeMillis() - startMs,
        Instant.ofEpochMilli(startMs),
        e == null ? null : e.getClass().getSimpleName());
  }

  public String getExportKey() {
    return exportKey;
  }

  public boolean isSuccess() {
    return success;
  }

  public int getMaxRows() {
    return maxRows;
  }

  public long getRows() {
    return rows;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public String getError() {
    return error;
  }
}
