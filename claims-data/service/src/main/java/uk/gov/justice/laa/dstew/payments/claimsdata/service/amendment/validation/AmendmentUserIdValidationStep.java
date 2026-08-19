package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.List;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Amendment metadata validation step: validates the submitting user's Entra identifier supplied
 * with a claim amendment.
 *
 * <p>This is a pure, in-memory structural check with no reference-data or external dependencies:
 * when a user id is supplied it must be a structurally valid UUID. The Claims API does not check
 * whether the user currently exists. It is deliberately kept separate from the governed
 * reference-data checks so it can run independently (and early) without touching the reference-data
 * provider.
 *
 * <p>A missing/undefined or explicitly null user id yields {@link
 * ClaimAmendmentValidationCode#INVALID_USER_IDENTIFIER_MISSING}, while a value that is present but
 * not a structurally valid UUID yields {@link
 * ClaimAmendmentValidationCode#INVALID_USER_IDENTIFIER_FORMAT}.
 */
@Component
public class AmendmentUserIdValidationStep implements ClaimAmendmentValidationStep {

  /**
   * Validates the submitting user's Entra identifier on the supplied state.
   *
   * @param claimAmendmentState the amendment in progress
   * @return the validation errors found by this step; an empty list means the step passed
   */
  @Override
  public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState claimAmendmentState) {
    ClaimAmendmentPayload payload = claimAmendmentState.getRequestPayload();
    String userId = unwrap(payload.getAmendmentUserId());

    if (userId == null) {
      return List.of(
          ClaimAmendmentValidationError.of(
              ClaimAmendmentValidationCode.INVALID_USER_IDENTIFIER_MISSING));
    }
    if (!Uuid7.isValidUuid(userId)) {
      return List.of(
          ClaimAmendmentValidationError.of(
              ClaimAmendmentValidationCode.INVALID_USER_IDENTIFIER_FORMAT));
    }
    return List.of();
  }
}
