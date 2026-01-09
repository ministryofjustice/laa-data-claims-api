package uk.gov.justice.laa.export.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Validated export request with typed params.
 */
public final class ValidatedExportRequest {
  private final Map<String, Object> params;
  private final int maxRows;
  private final Map<String, String[]> rawParams;

  /**
   * Creates a validated export request.
   */
  public ValidatedExportRequest(
      Map<String, Object> params,
      int maxRows,
      Map<String, String[]> rawParams) {
    this.params = params == null ? Map.of() : Map.copyOf(params);
    this.maxRows = maxRows;
    this.rawParams = rawParams == null ? Map.of() : Map.copyOf(rawParams);
  }

  public Map<String, Object> getParams() {
    return Collections.unmodifiableMap(params);
  }

  public Map<String, Object> getFilters() {
    return getParams();
  }


  public int getMaxRows() {
    return maxRows;
  }

  public Map<String, String[]> getRawParams() {
    return Collections.unmodifiableMap(rawParams);
  }

  /**
   * Returns a typed parameter value when present.
   */
  public <T> Optional<T> getFilter(String name, Class<T> type) {
    Object value = params.get(name);
    if (value == null) {
      return Optional.empty();
    }
    if (!type.isInstance(value)) {
      throw new IllegalArgumentException("Filter " + name + " is not of type " + type.getSimpleName());
    }
    return Optional.of(type.cast(value));
  }

  /**
   * Returns a typed list parameter value when present.
   */
  @SuppressWarnings("unchecked")
  public <T> Optional<List<T>> getFilterList(String name, Class<T> type) {
    Object value = params.get(name);
    if (value == null) {
      return Optional.empty();
    }
    if (!(value instanceof List<?> list)) {
      throw new IllegalArgumentException("Filter " + name + " is not a list");
    }
    for (Object item : list) {
      if (!type.isInstance(item)) {
        throw new IllegalArgumentException(
            "Filter " + name + " list contains non-" + type.getSimpleName() + " values");
      }
    }
    return Optional.of((List<T>) list);
  }

  public <T> Optional<T> getParam(String name, Class<T> type) {
    return getFilter(name, type);
  }

  public <T> Optional<List<T>> getParamList(String name, Class<T> type) {
    return getFilterList(name, type);
  }
}
