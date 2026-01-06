package uk.gov.justice.laa.export;

import java.util.Map;
import uk.gov.justice.laa.export.model.ExportDefinition;
import uk.gov.justice.laa.export.model.ValidatedExportRequest;

/**
 * Validates and normalizes export requests.
 */
public interface ExportRequestValidator {
  ValidatedExportRequest validate(ExportDefinition def, Map<String, String[]> rawParams);
}
