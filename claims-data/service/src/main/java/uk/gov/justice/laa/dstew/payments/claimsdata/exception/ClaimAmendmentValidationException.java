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
   * <p>The incoming errors are automatically sorted in descending priority order:
   *
   * <ol>
   *   <li>Fatal errors ({@code isFatal() == true}) take precedence over non-fatal errors.
   *   <li>Errors are then sorted by their numeric HTTP status code values in descending order
   *       (e.g., 500 before 400).
   * </ol>
   *
   * @param errors the collected validation errors discovered during the amendment process
   */
  public ClaimAmendmentValidationException(List<ClaimAmendmentValidationError> errors) {
    super("Claim amendment validation failed with " + errors.size() + " errors");

    // Sort errors: fatal first (true -> false), then HTTP status code descending (500 -> 400)
    this.errors = errors;
  }
}
