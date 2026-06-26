package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import java.util.List;
import lombok.Getter;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;

/**
 * Thrown when the synchronous claim amendment orchestrator detects validation failures. Carries the
 * complete list of collected errors so the global exception handler can map them into a 400 Bad
 * Request response.
 */
@Getter
public class ClaimAmendmentValidationException extends RuntimeException {

  private final List<ClaimAmendmentValidationError> errors;

  public ClaimAmendmentValidationException(List<ClaimAmendmentValidationError> errors) {
    super("Claim amendment validation failed with " + errors.size() + " errors");
    this.errors = errors;
  }
}
