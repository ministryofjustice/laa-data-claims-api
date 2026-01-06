package uk.gov.justice.laa.export.model;

import java.util.Collections;
import java.util.List;

/**
 * Configured export definition.
 */
public final class ExportDefinition {
  private final String key;
  private final String description;
  private final List<String> roles;
  private final int maxRows;
  private final String provider;
  private final List<ExportColumn> columns;
  private final List<ExportParamDefinition> params;

  /**
   * Creates an export definition.
   */
  public ExportDefinition(
      String key,
      String description,
      List<String> roles,
      int maxRows,
      String provider,
      List<ExportColumn> columns,
      List<ExportParamDefinition> params) {
    this.key = key;
    this.description = description;
    this.roles = roles == null ? List.of() : List.copyOf(roles);
    this.maxRows = maxRows;
    this.provider = provider;
    this.columns = columns == null ? List.of() : List.copyOf(columns);
    this.params = params == null ? List.of() : List.copyOf(params);
  }

  public String getKey() {
    return key;
  }

  public String getDescription() {
    return description;
  }

  public List<String> getRoles() {
    return Collections.unmodifiableList(roles);
  }

  public int getMaxRows() {
    return maxRows;
  }

  public String getProvider() {
    return provider;
  }

  public List<ExportColumn> getColumns() {
    return Collections.unmodifiableList(columns);
  }

  public List<ExportParamDefinition> getParams() {
    return Collections.unmodifiableList(params);
  }

}
