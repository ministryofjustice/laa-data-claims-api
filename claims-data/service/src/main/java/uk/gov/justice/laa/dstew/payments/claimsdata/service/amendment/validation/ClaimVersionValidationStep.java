package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Component
public class ClaimVersionValidationStep implements ClaimAmendmentValidationStep {

  /**
   * Validates the requested amendment version against the current claim snapshot.
   *
   * @param state the in-memory amendment state containing both the before-state snapshot and the
   *     requested patch payload
   * @return a list containing a {@code CLAIM_VERSION_CONFLICT} error if the versions do not match,
   *     or an empty list if validation passes
   */
  @Override
  public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state) {
    if (state == null || (state.getBeforeState() == null || state.getRequestPayload() == null)) {
      return List.of(
          ClaimAmendmentValidationError.of(ClaimAmendmentValidationCode.INVALID_NULL_VERSION));
    }

    Long expectedVersion = state.getBeforeState().getVersion();
    // Read the submitted version defensively: an undefined JsonNullable (client sent no version)
    // and an explicit null both mean "no version supplied" and are treated as a null-version error.
    Long submittedVersion = state.getRequestPayload().getVersion().orElse(null);

    if (submittedVersion == null) {
      return List.of(
          ClaimAmendmentValidationError.of(ClaimAmendmentValidationCode.INVALID_NULL_VERSION));
    } else if (!submittedVersion.equals(expectedVersion)) {
      // Full Long equality: guards against a null stored version (no NPE) and avoids the value
      // truncation that Long#intValue would introduce for versions beyond Integer.MAX_VALUE.
      // Structured warning for support/investigation. Safe fields only - never the amendment
      // payload values or financial details carried on the snapshot.
      log.warn(
          "event={} claimId={} submittedClaimVersion={} currentClaimVersion={} conflictPoint={}",
          ClaimAmendmentValidationCode.CLAIM_VERSION_CONFLICT.name(),
          state.getBeforeState().getClaimId(),
          submittedVersion,
          expectedVersion,
          "initial_check");
      return List.of(
          ClaimAmendmentValidationError.of(ClaimAmendmentValidationCode.CLAIM_VERSION_CONFLICT));
    }
    return List.of();
  }
}
