package uk.gov.justice.laa.export.service;

import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import uk.gov.justice.laa.export.ExportAuditSink;
import uk.gov.justice.laa.export.ExportLimitExceededException;
import uk.gov.justice.laa.export.ExportQueryProvider;
import uk.gov.justice.laa.export.ExportRegistry;
import uk.gov.justice.laa.export.ExportRequestValidator;
import uk.gov.justice.laa.export.ExportSecurity;
import uk.gov.justice.laa.export.ExportService;
import uk.gov.justice.laa.export.csv.CsvRowWriter;
import uk.gov.justice.laa.export.model.ExportAuditEvent;
import uk.gov.justice.laa.export.model.ExportDefinition;
import uk.gov.justice.laa.export.model.ValidatedExportRequest;
import uk.gov.justice.laa.export.tx.TransactionalStreamRunner;

/**
 * Default export service implementation.
 */
public final class DefaultExportService implements ExportService {
  private final ExportRegistry registry;
  private final ExportRequestValidator validator;
  private final TransactionalStreamRunner txRunner;
  private final CsvRowWriter csvWriter;
  private final ExportAuditSink audit;
  private final ExportSecurity security;

  /**
   * Constructor for default export service.
   */
  public DefaultExportService(
      ExportRegistry registry,
      ExportRequestValidator validator,
      TransactionalStreamRunner txRunner,
      CsvRowWriter csvWriter,
      ExportAuditSink audit,
      ExportSecurity security) {
    this.registry = registry;
    this.validator = validator;
    this.txRunner = txRunner;
    this.csvWriter = csvWriter;
    this.audit = audit;
    this.security = security;
  }

  /**
   * Streams a CSV export for the given key and raw parameters.
   */
  @Override
  public void streamCsv(String exportKey, Map<String, String[]> rawParams, OutputStream out) {
    ExportDefinition def = registry.getRequired(exportKey);
    security.checkAllowed(def);
    ValidatedExportRequest validated = validator.validate(def, rawParams);

    long start = System.currentTimeMillis();
    AtomicLong rowCounter = new AtomicLong();

    try {
      ExportQueryProvider<?> provider = registry.getProvider(exportKey);
      writeStream(def, provider, validated, out, rowCounter);

      audit.record(ExportAuditEvent.success(exportKey, validated, rowCounter.get(), start));
    } catch (Exception e) {
      audit.record(ExportAuditEvent.failure(exportKey, validated, rowCounter.get(), start, e));
      throw e;
    }
  }

  @SuppressWarnings("unchecked")
  private <T> void writeStream(
      ExportDefinition def,
      ExportQueryProvider<?> rawProvider,
      ValidatedExportRequest validated,
      OutputStream out,
      AtomicLong rowCounter) {
    ExportQueryProvider<T> provider = (ExportQueryProvider<T>) rawProvider;
    csvWriter.writeAll(
        out,
        provider.rowType(),
        def.getColumns(),
        rowConsumer ->
            txRunner.run(
                () -> provider.fetch(validated),
                row -> {
                  long n = rowCounter.incrementAndGet();
                  if (n > validated.getMaxRows()) {
                    throw new ExportLimitExceededException(
                        "Row limit exceeded: " + validated.getMaxRows());
                  }
                  rowConsumer.accept(row);
                }));
  }
}
