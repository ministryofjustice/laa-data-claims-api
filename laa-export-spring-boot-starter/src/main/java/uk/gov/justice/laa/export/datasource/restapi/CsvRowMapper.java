package uk.gov.justice.laa.export.datasource.restapi;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface CsvRowMapper {

  /**
   * Convert a JSON object (Map) to CSV columns.
   */
  List<String> mapRow(Map<String, Object> json);
}