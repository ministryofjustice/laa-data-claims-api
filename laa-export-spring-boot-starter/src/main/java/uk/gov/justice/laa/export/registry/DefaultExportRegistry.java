package uk.gov.justice.laa.export.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;
import uk.gov.justice.laa.export.ExportConfigurationException;
import uk.gov.justice.laa.export.ExportCsvProvider;
import uk.gov.justice.laa.export.ExportDefinitionNotFoundException;
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
  private final Map<String, ExportCsvProvider> providers;

  /**
   * Creates the registry from bound properties and provider beans.
   */
  public DefaultExportRegistry(
      ApplicationContext applicationContext, LaaExportsProperties properties) {
    this.definitions = new HashMap<>();
    this.providers = new HashMap<>();

    Map<String, LaaExportsProperties.Definition> mergedDefinitions = new HashMap<>();
    mergedDefinitions.putAll(loadDefinitionsFromResources());
    mergedDefinitions.putAll(properties.getDefinitions());

    int defaultMaxRows = properties.getDefaults().getMaxRows();
    for (Map.Entry<String, LaaExportsProperties.Definition> entry :
        mergedDefinitions.entrySet()) {
      String key = entry.getKey();
      LaaExportsProperties.Definition definition = entry.getValue();
      if (definition.getProvider() == null || definition.getProvider().isBlank()) {
        throw new ExportConfigurationException("Export " + key + " missing provider");
      }
      ExportCsvProvider provider =
          applicationContext.getBean(definition.getProvider(), ExportCsvProvider.class);
      if (providers.containsKey(key)) {
        throw new ExportConfigurationException("Duplicate export key: " + key);
      }
      providers.put(key, provider);

      int maxRows = definition.getMaxRows() == null ? defaultMaxRows : definition.getMaxRows();
      ExportDefinition def =
          new ExportDefinition(
              key,
              definition.getDescription(),
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
  public ExportCsvProvider getProvider(String key) {
    ExportCsvProvider provider = providers.get(key);
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

  private Map<String, LaaExportsProperties.Definition> loadDefinitionsFromResources() {
    Map<String, LaaExportsProperties.Definition> loaded = new HashMap<>();
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources = resolveDefinitionResources(resolver);
    if (resources.length == 0) {
      return loaded;
    }
    ObjectMapper mapper = new ObjectMapper();
    Yaml yaml = new Yaml();
    for (Resource resource : resources) {
      Map<String, Object> root = readYaml(resource, yaml);
      if (root.isEmpty()) {
        continue;
      }
      Map<String, Object> definitions = extractDefinitions(root);
      if (!definitions.isEmpty()) {
        mergeDefinitions(mapper, loaded, definitions);
        continue;
      }
      if (root.containsKey("sql") || root.containsKey("provider")) {
        String key = filenameKey(resource);
        loaded.put(key, mapper.convertValue(root, LaaExportsProperties.Definition.class));
        continue;
      }
      if (!root.isEmpty()) {
        mergeDefinitions(mapper, loaded, root);
      }
    }
    return loaded;
  }

  private Resource[] resolveDefinitionResources(PathMatchingResourcePatternResolver resolver) {
    try {
      Resource[] yml = resolver.getResources("classpath*:export_definitions/*.yml");
      Resource[] yaml = resolver.getResources("classpath*:export_definitions/*.yaml");
      Resource[] resources = new Resource[yml.length + yaml.length];
      System.arraycopy(yml, 0, resources, 0, yml.length);
      System.arraycopy(yaml, 0, resources, yml.length, yaml.length);
      return resources;
    } catch (Exception e) {
      throw new ExportConfigurationException("Failed to load export definition resources", e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readYaml(Resource resource, Yaml yaml) {
    try (InputStream input = resource.getInputStream()) {
      Object loaded = yaml.load(input);
      if (loaded instanceof Map<?, ?> map) {
        return (Map<String, Object>) map;
      }
      return Map.of();
    } catch (Exception e) {
      throw new ExportConfigurationException(
          "Failed to parse export definition: " + filenameKey(resource), e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> extractDefinitions(Map<String, Object> root) {
    Object laa = root.get("laa");
    if (!(laa instanceof Map<?, ?> laaMap)) {
      return Map.of();
    }
    Map<String, Object> starterMap = null;
    Object springboot = ((Map<String, Object>) laaMap).get("springboot");
    if (springboot instanceof Map<?, ?> springbootMap) {
      Object starter = ((Map<String, Object>) springbootMap).get("starter");
      if (starter instanceof Map<?, ?> starterValue) {
        starterMap = (Map<String, Object>) starterValue;
      }
    }
    if (starterMap == null) {
      Object dottedStarter = ((Map<String, Object>) laaMap).get("springboot.starter");
      if (dottedStarter instanceof Map<?, ?> starterValue) {
        starterMap = (Map<String, Object>) starterValue;
      }
    }
    if (starterMap == null) {
      return Map.of();
    }
    Object exportsConfig = starterMap.get("exports");
    if (!(exportsConfig instanceof Map<?, ?> exportsConfigMap)) {
      return Map.of();
    }
    Object definitions = ((Map<String, Object>) exportsConfigMap).get("definitions");
    if (!(definitions instanceof Map<?, ?> defsMap)) {
      return Map.of();
    }
    return (Map<String, Object>) defsMap;
  }

  private void mergeDefinitions(
      ObjectMapper mapper,
      Map<String, LaaExportsProperties.Definition> target,
      Map<String, Object> definitions) {
    definitions.forEach(
        (key, value) ->
            target.put(key, mapper.convertValue(value, LaaExportsProperties.Definition.class)));
  }

  private String filenameKey(Resource resource) {
    String filename = resource.getFilename();
    if (filename == null) {
      return "export";
    }
    int dot = filename.lastIndexOf('.');
    return dot > 0 ? filename.substring(0, dot) : filename;
  }
}
