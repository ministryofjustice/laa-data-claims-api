package uk.gov.justice.laa.dstew.payments.claimsdata.validator;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFileReadException;

/**
 * An error handler implementation that collects XML validation errors during SAX parsing. This
 * handler is specifically designed to work with XML schema validation to collect and format
 * validation errors in a user-friendly way for bulk claim submissions.
 *
 * <p>The handler: - Ignores warnings as they are not critical for validation - Collects both
 * regular and fatal errors during parsing - Formats error messages to include line and column
 * numbers for easy debugging - Can throw a {@link BulkSubmissionFileReadException} containing a
 * specific error message - Logs detailed validation errors for support teams while providing safe
 * messages to end users
 *
 * @see org.xml.sax.ErrorHandler
 * @see org.xml.sax.SAXParseException
 * @see BulkSubmissionFileReadException
 */
@Slf4j
public class CollectingErrorHandler implements ErrorHandler {

  public static final String XML_XSD_VALIDATION_FAILED_MESSAGE =
      "The uploaded xml file does not conform to the XSD, please check your file and try again";

  private final List<String> errors = new ArrayList<>();

  /**
   * Handles validation warnings - currently ignored.
   *
   * @param exception The warning details
   */
  @Override
  public void warning(SAXParseException exception) {
    // Ignore warnings
  }

  /**
   * Handles validation errors by collecting the formatted error message.
   *
   * @param exception The error details to be collected
   */
  @Override
  public void error(SAXParseException exception) {
    errors.add(format(exception));
  }

  /**
   * Handles fatal validation errors by collecting the formatted error message.
   *
   * @param exception The fatal error details to be collected
   */
  @Override
  public void fatalError(SAXParseException exception) {
    errors.add(format(exception));
  }

  /**
   * Throws a {@link BulkSubmissionFileReadException} if any errors were collected during
   * validation. The exception message includes all collected errors formatted with line numbers.
   * Detailed error messages are logged at WARN level for support investigation.
   *
   * @throws BulkSubmissionFileReadException if any validation errors were collected
   */
  public void throwIfErrors() {
    if (!errors.isEmpty()) {

      // detailed logs for support
      log.warn("XML failed XSD validation:\n{}", String.join("\n", errors));

      // safe message for end users
      throw new BulkSubmissionFileReadException(XML_XSD_VALIDATION_FAILED_MESSAGE);
    }
  }

  private String format(SAXParseException ex) {
    return "Line "
        + ex.getLineNumber()
        + ", Column "
        + ex.getColumnNumber()
        + ": "
        + ex.getMessage();
  }
}
