package uk.gov.justice.laa.export.service;

import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import uk.gov.justice.laa.export.ExportAuditSink;
import uk.gov.justice.laa.export.ExportCsvProvider;
import uk.gov.justice.laa.export.ExportRegistry;
import uk.gov.justice.laa.export.ExportRequestValidator;
import uk.gov.justice.laa.export.ExportService;
import uk.gov.justice.laa.export.model.ExportAuditEvent;
import uk.gov.justice.laa.export.model.ExportDefinition;
import uk.gov.justice.laa.export.model.ValidatedExportRequest;

/**
 * Default export service implementation.
 */
public final class DefaultExportService implements ExportService {
  private final ExportRegistry registry;
  private final ExportRequestValidator validator;
  private final ExportAuditSink audit;

  /**
   * Constructor for default export service.
   */
  public DefaultExportService(
      ExportRegistry registry,
      ExportRequestValidator validator,
      ExportAuditSink audit) {
    this.registry = registry;
    this.validator = validator;
    this.audit = audit;
  }

  /**
   * Streams a CSV export for the given key and raw parameters.
   */
  @Override
  public void streamCsv(String exportKey, Map<String, String[]> rawParams, OutputStream out) {
    ExportDefinition def = registry.getRequired(exportKey);
    ValidatedExportRequest validated = validator.validate(def, rawParams);

    long start = System.currentTimeMillis();
    AtomicLong rowCounter = new AtomicLong();

    try {
      ExportCsvProvider provider = registry.getProvider(exportKey);
      long rows = provider.writeCsv(validated, out, def.getColumns());
      if (rows >= 0) {
        rowCounter.set(rows);
      }

      audit.record(ExportAuditEvent.success(exportKey, validated, rowCounter.get(), start));
    } catch (Exception e) {
      audit.record(ExportAuditEvent.failure(exportKey, validated, rowCounter.get(), start, e));
      throw e;
    }
  }
}
