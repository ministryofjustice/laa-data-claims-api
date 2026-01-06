package uk.gov.justice.laa.export;

import java.util.Set;
import uk.gov.justice.laa.export.model.ExportDefinition;

/**
 * Resolves export definitions and providers.
 */
public interface ExportRegistry {
  ExportDefinition getRequired(String key);

  ExportQueryProvider<?> getProvider(String key);

  Set<String> keys();
}
