package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the governed amendment metadata reference data (Requested By / Amendment Reason) is
 * unavailable at submit time and the amendment metadata therefore cannot be validated.
 *
 * <p>This represents a controlled technical failure: the flow fails safely and saves nothing.
 * Extending {@link ClaimsDataException} maps it to a {@link HttpStatus#SERVICE_UNAVAILABLE} RFC
 * 9457 Problem Detail, surfacing the user-facing display message while the technical detail is
 * logged.
 */
public class AmendmentReferenceDataUnavailableException extends ClaimsDataException {

  private static final String DISPLAY_MESSAGE =
      "A technical error occurred, please try again after some time";

  /** Construct the exception with the standard user-facing display message. */
  public AmendmentReferenceDataUnavailableException() {
    super(DISPLAY_MESSAGE, HttpStatus.SERVICE_UNAVAILABLE);
  }

  /**
   * Construct the exception with the standard user-facing display message and an underlying cause.
   *
   * @param cause the underlying cause (e.g. a data access failure)
   */
  public AmendmentReferenceDataUnavailableException(Throwable cause) {
    super(DISPLAY_MESSAGE, HttpStatus.SERVICE_UNAVAILABLE, cause);
  }
}
