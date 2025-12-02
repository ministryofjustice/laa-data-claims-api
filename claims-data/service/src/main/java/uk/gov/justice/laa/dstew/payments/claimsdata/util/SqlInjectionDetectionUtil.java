package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import io.micrometer.common.util.StringUtils;
import java.util.Map;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvBulkSubmissionRow;

/** Utility class for detecting potential SQL injection patterns in input values. */
public class SqlInjectionDetectionUtil {

  // SQL keyword blacklist
  private static final String[] SQL_KEYWORDS = {
    "select ",
    "insert ",
    "update ",
    "delete ",
    "drop ",
    "alter ",
    "truncate ",
    "exec ",
    "execute ",
    "--",
    ";",
    "/*",
    "*/",
    " or ",
    " and "
  };

  /**
   * Validates that none of the values in the given {@link CsvBulkSubmissionRow} contain SQL
   * injection keywords.
   *
   * @param row the CSV row containing key-value pairs to validate
   * @throws IllegalArgumentException if any value contains a SQL injection pattern
   */
  public static void validateNoSqlInjection(CsvBulkSubmissionRow row) {
    for (Map.Entry<String, String> entry : row.values().entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      checkValueContainsAnySqlInjectionKeywords(key, value);
    }
  }

  /**
   * Validates that the given value does not contain SQL injection keywords.
   *
   * @param key the name of the field being validated (used for error messages)
   * @param value the value to validate
   * @throws IllegalArgumentException if the value contains a SQL injection pattern
   */
  public static void validateNoSqlInjection(String key, String value) {
    checkValueContainsAnySqlInjectionKeywords(key, value);
  }

  /**
   * Checks if the given value contains any SQL injection keywords and throws an exception if found.
   *
   * @param key the name of the field being validated
   * @param value the value to check
   * @throws IllegalArgumentException if the value contains a SQL injection pattern
   */
  private static void checkValueContainsAnySqlInjectionKeywords(String key, String value) {
    if (StringUtils.isBlank(value)) {
      return;
    }
    String lower = value.toLowerCase();
    for (String keyword : SQL_KEYWORDS) {
      if (lower.contains(keyword)) {
        throw new IllegalArgumentException(
            "SQL injection pattern detected in field '" + key + "' with value '" + value + "'");
      }
    }
  }
}
