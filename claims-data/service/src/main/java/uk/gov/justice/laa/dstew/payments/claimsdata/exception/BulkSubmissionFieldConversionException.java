package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import uk.gov.laa.springboot.exception.ApplicationException;

/**
 * Exception raised when a bulk submission field cannot be converted to its target type.
 *
 * <p>This extends {@link uk.gov.laa.springboot.exception.ApplicationException} so the framework can
 * automatically translate the error into a {@link org.springframework.http.HttpStatus#BAD_REQUEST}
 * response. The offending field name and value are captured to support client-side debugging.
 */
public class BulkSubmissionFieldConversionException extends ApplicationException {

  private final String fieldName;
  private final String rejectedValue;

  /**
   * Creates a new conversion exception.
   *
   * @param fieldName the name of the field that failed conversion
   * @param rejectedValue the value that could not be converted
   */
  public BulkSubmissionFieldConversionException(
      final String fieldName, final String rejectedValue) {
    super(buildMessage(fieldName, rejectedValue, false), HttpStatus.BAD_REQUEST);
    this.fieldName = fieldName;
    this.rejectedValue = rejectedValue;
  }

  /**
   * Creates a new conversion exception.
   *
   * @param fieldName the name of the field that failed conversion
   * @param rejectedValue the value that could not be converted
   * @param isBooleanField indicates if the field is a boolean field expecting 'Y' or 'N' values
   */
  public BulkSubmissionFieldConversionException(
      final String fieldName, final String rejectedValue, final boolean isBooleanField) {
    super(buildMessage(fieldName, rejectedValue, isBooleanField), HttpStatus.BAD_REQUEST);
    this.fieldName = fieldName;
    this.rejectedValue = rejectedValue;
  }

  /**
   * Creates a new conversion exception with an underlying cause.
   *
   * @param fieldName the name of the field that failed conversion
   * @param rejectedValue the value that could not be converted
   * @param cause the root cause of the conversion failure
   */
  public BulkSubmissionFieldConversionException(
      final String fieldName, final String rejectedValue, final Exception cause) {
    this(fieldName, rejectedValue);
    initCause(cause);
  }

  public String getFieldName() {
    return fieldName;
  }

  public String getRejectedValue() {
    return rejectedValue;
  }

  private static String buildMessage(
      final String fieldName, final String rejectedValue, boolean isBooleanField) {
    String safeFieldName = StringUtils.hasText(fieldName) ? fieldName : "unknown field";
    String safeRejectedValue = StringUtils.hasText(rejectedValue) ? rejectedValue : "blank";
    String errorMessage =
        "Invalid value '" + safeRejectedValue + "' supplied for field '" + safeFieldName + "'.";
    if (isBooleanField) {
      errorMessage += " Valid values are 'Y' or 'N'";
    }
    return errorMessage;
  }
}
