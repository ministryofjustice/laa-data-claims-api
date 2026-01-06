package uk.gov.justice.laa.export;

import java.util.stream.Stream;
import uk.gov.justice.laa.export.model.ValidatedExportRequest;

/**
 * Supplies export rows for a validated request.
 */
public interface ExportQueryProvider<T> {
  Stream<T> fetch(ValidatedExportRequest request);

  Class<T> rowType();
}
