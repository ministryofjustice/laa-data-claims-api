package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import java.util.List;
import lombok.Getter;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;

/**
 * Exception thrown when one or more claim amendment validation steps fail.
 *
 * <p>This exception automatically sorts the underlying validation errors upon construction.
 * High-priority errors—defined as fatal errors first, followed by errors with the highest HTTP
 * status values (e.g., 500, 409, 400)—are bubbled up to the top of the list.
 */
@Getter
public class ClaimAmendmentValidationException extends RuntimeException {

  /** The sorted list of validation errors associated with this failure. */
  private final List<ClaimAmendmentValidationError> errors;

  /**
   * Constructs a new exception containing the provided list of validation errors.
   *
   * <p>The provided errors are preserved in the exact order they are received. Any prioritization
   * or sorting (e.g., by fatality or HTTP status code) is expected to be handled by the caller
   * (such as the Data Handler) prior to throwing this exception.
   *
   * @param errors the collected validation errors discovered during the amendment process
   */
  public ClaimAmendmentValidationException(List<ClaimAmendmentValidationError> errors) {
    super("Claim amendment validation failed with " + errors.size() + " errors");
    this.errors = errors;
  }
}
