package uk.gov.justice.laa.export;

import uk.gov.justice.laa.export.model.ExportDefinition;

/**
 * Checks whether an export is allowed for the current user.
 */
public interface ExportSecurity {
  void checkAllowed(ExportDefinition def);
}
