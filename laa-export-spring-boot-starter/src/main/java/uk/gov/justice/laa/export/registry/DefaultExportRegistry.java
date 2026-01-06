package uk.gov.justice.laa.export.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationContext;
import uk.gov.justice.laa.export.ExportConfigurationException;
import uk.gov.justice.laa.export.ExportDefinitionNotFoundException;
import uk.gov.justice.laa.export.ExportQueryProvider;
import uk.gov.justice.laa.export.ExportRegistry;
import uk.gov.justice.laa.export.config.LaaExportsProperties;
import uk.gov.justice.laa.export.model.ExportColumn;
import uk.gov.justice.laa.export.model.ExportDefinition;
import uk.gov.justice.laa.export.model.ExportParamDefinition;

/**
 * Resolves configured exports and providers.
 */
public class DefaultExportRegistry implements ExportRegistry {
  private final Map<String, ExportDefinition> definitions;
  private final Map<String, ExportQueryProvider<?>> providers;

  /**
   * Creates the registry from bound properties and provider beans.
   */
  public DefaultExportRegistry(
      ApplicationContext applicationContext, LaaExportsProperties properties) {
    this.definitions = new HashMap<>();
    this.providers = new HashMap<>();

    int defaultMaxRows = properties.getDefaults().getMaxRows();
    for (Map.Entry<String, LaaExportsProperties.Definition> entry :
        properties.getDefinitions().entrySet()) {
      String key = entry.getKey();
      LaaExportsProperties.Definition definition = entry.getValue();
      if (definition.getProvider() == null || definition.getProvider().isBlank()) {
        throw new ExportConfigurationException("Export " + key + " missing provider");
      }
      ExportQueryProvider<?> provider =
          applicationContext.getBean(definition.getProvider(), ExportQueryProvider.class);
      if (providers.containsKey(key)) {
        throw new ExportConfigurationException("Duplicate export key: " + key);
      }
      providers.put(key, provider);

      int maxRows = definition.getMaxRows() == null ? defaultMaxRows : definition.getMaxRows();
      ExportDefinition def =
          new ExportDefinition(
              key,
              definition.getDescription(),
              definition.getRoles(),
              maxRows,
              definition.getProvider(),
              definition.getColumns().stream()
                  .map(c -> new ExportColumn(c.getKey(), c.getHeader(), c.getFormat()))
                  .collect(Collectors.toList()),
              definition.getParams().stream()
                  .map(
                      p ->
                          new ExportParamDefinition(
                              p.getName(),
                              parseFilterType(p.getType()),
                              p.getEnumClass(),
                              p.getAllowed(),
                              p.isRequired(),
                              p.getDefaultValue()))
                  .collect(Collectors.toList()));
      definitions.put(key, def);
    }
  }

  @Override
  public ExportDefinition getRequired(String key) {
    ExportDefinition def = definitions.get(key);
    if (def == null) {
      throw new ExportDefinitionNotFoundException("Export not found: " + key);
    }
    return def;
  }

  @Override
  public ExportQueryProvider<?> getProvider(String key) {
    ExportQueryProvider<?> provider = providers.get(key);
    if (provider == null) {
      throw new ExportDefinitionNotFoundException("Export provider not found: " + key);
    }
    return provider;
  }

  @Override
  public Set<String> keys() {
    return Set.copyOf(definitions.keySet());
  }

  private String parseFilterType(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ExportConfigurationException("Filter type missing");
    }
    String normalized = raw.trim().toUpperCase();
    if (!isSupportedType(normalized)) {
      throw new ExportConfigurationException("Invalid filter type: " + raw);
    }
    return normalized;
  }

  private boolean isSupportedType(String type) {
    return "STRING".equals(type)
        || "UUID".equals(type)
        || "INT".equals(type)
        || "LONG".equals(type)
        || "BOOLEAN".equals(type)
        || "DATE".equals(type)
        || "ENUM".equals(type);
  }
}
