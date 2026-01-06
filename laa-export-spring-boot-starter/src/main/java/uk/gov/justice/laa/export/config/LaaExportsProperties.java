package uk.gov.justice.laa.export.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for exports.
 */
@ConfigurationProperties(prefix = "laa.exports")
public class LaaExportsProperties {
  private boolean enabled = false;
  private Web web = new Web();
  private Defaults defaults = new Defaults();
  private Map<String, Definition> definitions = new HashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Web getWeb() {
    return web;
  }

  public void setWeb(Web web) {
    this.web = web;
  }

  public Defaults getDefaults() {
    return defaults;
  }

  public void setDefaults(Defaults defaults) {
    this.defaults = defaults;
  }

  public Map<String, Definition> getDefinitions() {
    return definitions;
  }

  public void setDefinitions(Map<String, Definition> definitions) {
    this.definitions = definitions;
  }

  /**
   * Web endpoint settings.
   */
  public static class Web {
    private boolean enabled = true;
    private String basePath = "/exports";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getBasePath() {
      return basePath;
    }

    public void setBasePath(String basePath) {
      this.basePath = basePath;
    }
  }

  /**
   * Default export settings.
   */
  public static class Defaults {
    private int maxRows = 50000;

    public int getMaxRows() {
      return maxRows;
    }

    public void setMaxRows(int maxRows) {
      this.maxRows = maxRows;
    }
  }

  /**
   * Per-export definition settings.
   */
  public static class Definition {
    private String description;
    private List<String> roles = new ArrayList<>();
    private Integer maxRows;
    private String provider;
    private String sql;
    private List<Column> columns = new ArrayList<>();
    private List<Param> params = new ArrayList<>();

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public List<String> getRoles() {
      return roles;
    }

    public void setRoles(List<String> roles) {
      this.roles = roles;
    }

    public Integer getMaxRows() {
      return maxRows;
    }

    public void setMaxRows(Integer maxRows) {
      this.maxRows = maxRows;
    }

    public String getProvider() {
      return provider;
    }

    public void setProvider(String provider) {
      this.provider = provider;
    }

    public String getSql() {
      return sql;
    }

    public void setSql(String sql) {
      this.sql = sql;
    }

    public List<Column> getColumns() {
      return columns;
    }

    public void setColumns(List<Column> columns) {
      this.columns = columns;
    }

    public List<Param> getParams() {
      return params;
    }

    public void setParams(List<Param> params) {
      this.params = params;
    }

  }

  /**
   * CSV column configuration.
   */
  public static class Column {
    private String key;
    private String header;
    private String format;

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public String getHeader() {
      return header;
    }

    public void setHeader(String header) {
      this.header = header;
    }

    public String getFormat() {
      return format;
    }

    public void setFormat(String format) {
      this.format = format;
    }
  }

  /**
   * Request parameter configuration.
   */
  public static class Param {
    private String name;
    private String type;
    private String enumClass;
    private List<String> allowed = new ArrayList<>();
    private boolean required;
    private String defaultValue;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getEnumClass() {
      return enumClass;
    }

    public void setEnumClass(String enumClass) {
      this.enumClass = enumClass;
    }

    public List<String> getAllowed() {
      return allowed;
    }

    public void setAllowed(List<String> allowed) {
      this.allowed = allowed;
    }

    public boolean isRequired() {
      return required;
    }

    public void setRequired(boolean required) {
      this.required = required;
    }

    public String getDefaultValue() {
      return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
      this.defaultValue = defaultValue;
    }
  }

}
