package uk.gov.justice.laa.export.security;

import uk.gov.justice.laa.export.ExportSecurity;
import uk.gov.justice.laa.export.model.ExportDefinition;

/**
 * Security implementation that allows all exports.
 */
public class PermitAllExportSecurity implements ExportSecurity {
  @Override
  public void checkAllowed(ExportDefinition def) {
    // No-op for environments without security
  }
}
