package uk.gov.justice.laa.export;

import uk.gov.justice.laa.export.model.ExportAuditEvent;

/**
 * Records export audit events.
 */
public interface ExportAuditSink {
  void record(ExportAuditEvent event);
}
