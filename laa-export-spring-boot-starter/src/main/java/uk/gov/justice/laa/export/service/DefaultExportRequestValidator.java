package uk.gov.justice.laa.export.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import uk.gov.justice.laa.export.ExportRequestValidator;
import uk.gov.justice.laa.export.ExportValidationException;
import uk.gov.justice.laa.export.model.ExportDefinition;
import uk.gov.justice.laa.export.model.ExportParamDefinition;
import uk.gov.justice.laa.export.model.ValidatedExportRequest;

/**
 * Default validator for export requests.
 */
public class DefaultExportRequestValidator implements ExportRequestValidator {

  @Override
  public ValidatedExportRequest validate(ExportDefinition def, Map<String, String[]> rawParams) {
    Map<String, String[]> params = rawParams == null ? Map.of() : rawParams;
    Map<String, ExportParamDefinition> paramsByName =
        def.getParams().stream().collect(Collectors.toMap(ExportParamDefinition::getName, f -> f));

    Map<String, Object> parsedParams = new HashMap<>();
    for (Map.Entry<String, String[]> entry : params.entrySet()) {
      String name = entry.getKey();
      if ("maxRows".equals(name)) {
        continue;
      }
      ExportParamDefinition param = paramsByName.get(name);
      if (param == null) {
        throw new ExportValidationException("Unknown param: " + name);
      }
      parsedParams.put(name, parseParam(param, entry.getValue()));
    }

    for (ExportParamDefinition param : def.getParams()) {
      if (!parsedParams.containsKey(param.getName())) {
        Object defaultValue = parseDefault(param);
        if (defaultValue != null) {
          parsedParams.put(param.getName(), defaultValue);
          continue;
        }
        if (param.isRequired()) {
          throw new ExportValidationException("Missing required param: " + param.getName());
        }
        continue;
      }
      Object value = parsedParams.get(param.getName());
      if (param.isRequired()
          && (value == null || (value instanceof List<?> list && list.isEmpty()))) {
        throw new ExportValidationException("Missing required param: " + param.getName());
      }
    }

    int maxRows = def.getMaxRows();
    String[] maxRowsParam = params.get("maxRows");
    if (maxRowsParam != null && maxRowsParam.length > 0) {
      try {
        int requested = Integer.parseInt(maxRowsParam[0]);
        if (requested <= 0) {
          throw new ExportValidationException("maxRows must be positive");
        }
        maxRows = Math.min(requested, def.getMaxRows());
      } catch (NumberFormatException e) {
        throw new ExportValidationException("maxRows must be an integer");
      }
    }

    return new ValidatedExportRequest(parsedParams, maxRows, params);
  }

  private Object parseParam(ExportParamDefinition param, String[] values) {
    if (values == null || values.length == 0) {
      return parseDefault(param);
    }
    if (values.length == 1) {
      return parseSingle(param, values[0]);
    }
    List<Object> parsed = new ArrayList<>(values.length);
    for (String value : values) {
      parsed.add(parseSingle(param, value));
    }
    return parsed;
  }

  private Object parseSingle(ExportParamDefinition param, String rawValue) {
    String value = rawValue == null ? null : rawValue.trim();
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value;
    if ("ENUM".equalsIgnoreCase(param.getType())) {
      normalized = value.toUpperCase(Locale.UK);
    }
    if (!param.getAllowed().isEmpty() && !param.getAllowed().contains(normalized)) {
      throw new ExportValidationException(
          "Param " + param.getName() + " value not allowed: " + value);
    }
    String type = param.getType() == null ? "" : param.getType().trim().toUpperCase(Locale.UK);
    return switch (type) {
      case "STRING" -> value;
      case "UUID" -> parseUuid(param.getName(), value);
      case "INT" -> parseInt(param.getName(), value);
      case "LONG" -> parseLong(param.getName(), value);
      case "BOOLEAN" -> parseBoolean(param.getName(), value);
      case "DATE" -> parseDate(param.getName(), value);
      case "ENUM" -> parseEnum(param, normalized);
      default -> throw new ExportValidationException("Filter type invalid: " + param.getType());
    };
  }

  private UUID parseUuid(String name, String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new ExportValidationException("Filter " + name + " must be a UUID");
    }
  }

  private Integer parseInt(String name, String value) {
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException e) {
      throw new ExportValidationException("Filter " + name + " must be an integer");
    }
  }

  private Long parseLong(String name, String value) {
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException e) {
      throw new ExportValidationException("Filter " + name + " must be a long");
    }
  }

  private Boolean parseBoolean(String name, String value) {
    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
      throw new ExportValidationException("Filter " + name + " must be true or false");
    }
    return Boolean.parseBoolean(value);
  }

  private LocalDate parseDate(String name, String value) {
    try {
      return LocalDate.parse(value);
    } catch (Exception e) {
      throw new ExportValidationException("Filter " + name + " must be a date (yyyy-MM-dd)");
    }
  }

  @SuppressWarnings("unchecked")
  private Object parseEnum(ExportParamDefinition param, String value) {
    String enumClassName = param.getEnumClass();
    if (enumClassName == null || enumClassName.isBlank()) {
      throw new ExportValidationException("Param " + param.getName() + " enumClass missing");
    }
    try {
      Class<?> enumClass = Class.forName(enumClassName);
      if (!enumClass.isEnum()) {
        throw new ExportValidationException("Param " + param.getName() + " enumClass invalid");
      }
      return Enum.valueOf((Class<Enum>) enumClass, value.toUpperCase(Locale.UK));
    } catch (ExportValidationException e) {
      throw e;
    } catch (Exception e) {
      throw new ExportValidationException("Param " + param.getName() + " enum value invalid");
    }
  }

  private Object parseDefault(ExportParamDefinition param) {
    String defaultValue = param.getDefaultValue();
    if (defaultValue == null || defaultValue.isBlank()) {
      return null;
    }
    return parseSingle(param, defaultValue);
  }

}
