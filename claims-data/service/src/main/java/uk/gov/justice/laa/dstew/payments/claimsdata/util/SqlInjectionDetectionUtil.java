package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import io.micrometer.common.util.StringUtils;
import java.util.Map;
import java.util.Optional;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFileReadException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvBulkSubmissionRow;
import uk.gov.laa.springboot.sqlscanner.SqlScanner;

/**
 * Utility class for detecting potential SQL injection patterns in input values.
 *
 * <p>This class provides methods to validate input strings or collections of key-value pairs to
 * ensure they do not contain SQL injection keywords. It leverages {@link SqlScanner} for scanning
 * suspicious tokens.
 *
 * <p>Typical usage:
 *
 * <pre>
 *   SqlInjectionDetectionUtil.validateNoSqlInjection("username", userInput);
 *   SqlInjectionDetectionUtil.validateNoSqlInjection(csvRow);
 * </pre>
 */
public class SqlInjectionDetectionUtil {

  /**
   * Validates that none of the values in the given {@link CsvBulkSubmissionRow} contain SQL
   * injection keywords.
   *
   * @param row the CSV row containing key-value pairs to validate; must not be null
   * @throws BulkSubmissionFileReadException if any value contains a SQL injection pattern
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
   * @param key the name of the field being validated (used for error messages); must not be null
   * @param value the value to validate; may be null or blank
   * @throws BulkSubmissionFileReadException if the value contains a SQL injection pattern
   */
  public static void validateNoSqlInjection(String key, String value) {
    checkValueContainsAnySqlInjectionKeywords(key, value);
  }

  /**
   * Checks if the given value contains any SQL injection keywords and throws an exception if found.
   *
   * @param key the name of the field being validated; must not be null
   * @param value the value to check; may be null or blank
   * @throws BulkSubmissionFileReadException if the value contains a SQL injection pattern
   */
  private static void checkValueContainsAnySqlInjectionKeywords(String key, String value) {
    if (StringUtils.isBlank(value)) {
      return;
    }
    final SqlScanner sqlScanner = new SqlScanner();
    Optional<String> sqlTokens = sqlScanner.scan(value.toLowerCase());
    if (sqlTokens.isPresent()) {
      throw new BulkSubmissionFileReadException(
          "SQL injection pattern detected in field '" + key + "' with value '" + value + "'");
    }
  }
}
