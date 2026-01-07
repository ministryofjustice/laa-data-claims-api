package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import lombok.Getter;
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
@Getter
public class BulkSubmissionFieldConversionException extends ApplicationException {

  private final String exceptionMessage;
  private final String rejectedValue;

  /**
   * Creates a new conversion exception.
   *
   * @param exceptionMessage the name of the field that failed conversion
   * @param rejectedValue the value that could not be converted
   */
  public BulkSubmissionFieldConversionException(
      final String exceptionMessage, final String rejectedValue) {
    super(buildMessage(exceptionMessage, rejectedValue, false), HttpStatus.BAD_REQUEST);
    this.exceptionMessage = exceptionMessage;
    this.rejectedValue = rejectedValue;
  }

  /**
   * Creates a new conversion exception.
   *
   * @param exceptionMessage the name of the field that failed conversion
   * @param rejectedValue the value that could not be converted
   * @param isBooleanField indicates if the field is a boolean field expecting 'Y' or 'N' values
   */
  public BulkSubmissionFieldConversionException(
      final String exceptionMessage, final String rejectedValue, final boolean isBooleanField) {
    super(buildMessage(exceptionMessage, rejectedValue, isBooleanField), HttpStatus.BAD_REQUEST);
    this.exceptionMessage = exceptionMessage;
    this.rejectedValue = rejectedValue;
  }

  /**
   * Creates a new conversion exception with an underlying cause.
   *
   * @param exceptionMessage the name of the field that failed conversion
   * @param rejectedValue the value that could not be converted
   * @param cause the root cause of the conversion failure
   */
  public BulkSubmissionFieldConversionException(
      final String exceptionMessage, final String rejectedValue, final Exception cause) {
    this(exceptionMessage, rejectedValue);
    initCause(cause);
  }

  private static String buildMessage(
      final String exceptionMessage, final String rejectedValue, boolean isBooleanField) {
    String errorMessage =
        StringUtils.hasText(exceptionMessage) ? exceptionMessage : "unknown field";
    if (isBooleanField) {
      errorMessage =
          "Invalid value '"
              + rejectedValue
              + "' supplied for field '"
              + exceptionMessage
              + "'. Valid values are 'Y' or 'N'";
    }
    return errorMessage;
  }
}
