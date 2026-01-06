package uk.gov.justice.laa.export;

import java.io.OutputStream;
import java.util.Map;

/**
 * Streams export data to an output stream.
 */
public interface ExportService {
  void streamCsv(String exportKey, Map<String, String[]> rawParams, OutputStream out);
}
