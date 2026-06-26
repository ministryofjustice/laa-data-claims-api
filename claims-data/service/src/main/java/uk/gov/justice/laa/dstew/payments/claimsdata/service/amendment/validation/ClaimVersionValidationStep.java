package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.List;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;

/**
 * Validation step to ensure the claim version provided in the amendment request matches the current
 * version of the claim in the database.
 *
 * <p>This satisfies the claim-version contract (DSTEW-1751/1752) and acts as an early optimistic
 * locking mechanism. It prevents lost updates if the claim was modified by another process between
 * the user retrieving it and submitting the amendment.
 */
@Component
public class ClaimVersionValidationStep implements ClaimAmendmentValidationStep {

  /**
   * Validates the requested amendment version against the current claim snapshot.
   *
   * @param state the in-memory amendment state containing both the before-state snapshot and the
   *     requested patch payload
   * @return a list containing an {@code INVALID_CLAIM_VERSION_CONFLICT} error if the versions do
   *     not match, or an empty list if validation passes
   */
  @Override
  public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state) {
    Long expectedVersion = state.getBeforeState().getVersion();
    Long receivedVersion = state.getRequestPayload().getVersion().get();

    if (expectedVersion.intValue() != receivedVersion.intValue()) {
      return List.of(
          ClaimAmendmentValidationError.of(
              ClaimAmendmentValidationCode.INVALID_CLAIM_VERSION_CONFLICT));
    }
    return List.of();
  }
}
