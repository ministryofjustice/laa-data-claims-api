package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;

/**
 * Exception for issues encountered during validation of a claim amendment.
 * <p>Extending {@link RuntimeException} allows the exception to be caught
 * by the {@link DataClaimsExceptionHandler}.
 */

@Getter
public class ClaimAmendmentValidationException extends RuntimeException {

  private final List<ClaimAmendmentValidationError> errors;

  public ClaimAmendmentValidationException(List<ClaimAmendmentValidationError> errors) {
    super("Claim amendment validation failed with " + errors.size() + " errors");

    // Sort errors: fatal first (true -> false), then HTTP status code descending (500 -> 400)
    this.errors =
        errors.stream()
            .sorted(
                Comparator.comparing(
                        ClaimAmendmentValidationError::isFatal, Comparator.reverseOrder())
                    .thenComparing(
                        error -> error.getHttpStatus().value(), Comparator.reverseOrder()))
            .toList();
  }

  /**
   * Helper method to grab the highest priority error (e.g., if you need to throw or inspect the
   * first fatal error).
   */
  public ClaimAmendmentValidationError getPrimaryError() {
    return errors.isEmpty() ? null : errors.get(0);
  }
}
