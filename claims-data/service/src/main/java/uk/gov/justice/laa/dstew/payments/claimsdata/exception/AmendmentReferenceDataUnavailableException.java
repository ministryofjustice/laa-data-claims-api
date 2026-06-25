package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentMetadataValidationError;

/**
 * Thrown when the governed amendment metadata reference data (Requested By / Amendment Reason) is
 * unavailable at submit time and the amendment metadata therefore cannot be validated.
 *
 * <p>This represents the controlled technical failure {@code
 * TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA}: the flow fails safely and saves nothing.
 * Extending {@link ClaimsDataException} maps it to a {@link HttpStatus#SERVICE_UNAVAILABLE} RFC
 * 9457 Problem Detail, surfacing the user-facing display message while the technical detail is
 * logged.
 */
public class AmendmentReferenceDataUnavailableException extends ClaimsDataException {

  /** Construct the exception with the standard user-facing display message. */
  public AmendmentReferenceDataUnavailableException() {
    super(
        AmendmentMetadataValidationError.TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA
            .formatDisplayMessage(),
        HttpStatus.SERVICE_UNAVAILABLE);
  }

  /**
   * Construct the exception with the standard user-facing display message and an underlying cause.
   *
   * @param cause the underlying cause (e.g. a data access failure)
   */
  public AmendmentReferenceDataUnavailableException(Throwable cause) {
    super(
        AmendmentMetadataValidationError.TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA
            .formatDisplayMessage(),
        HttpStatus.SERVICE_UNAVAILABLE,
        cause);
  }
}
