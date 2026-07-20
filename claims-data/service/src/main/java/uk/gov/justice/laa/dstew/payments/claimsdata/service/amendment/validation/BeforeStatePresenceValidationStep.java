package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;

/**
 * Fail-fast guard that the amendment before-state snapshot is present.
 *
 * <p>The before-state is built from an already-retrieved claim (a missing claim is rejected with
 * {@code ClaimNotFoundException} before validation runs), so a {@code null} before-state is an
 * internal invariant breach - the snapshot mapper produced nothing for a claim that exists - rather
 * than a user-correctable condition. Rather than let a later step dereference it and fail with an
 * opaque {@link NullPointerException}, this step reports a fatal {@link
 * ClaimAmendmentValidationCode#TECHNICAL_ERROR_MISSING_CLAIM_STATE} so the flow halts with a
 * diagnosable error.
 *
 * <p>It runs immediately after the feature-flag gate and before any step that reads the
 * before-state (e.g. {@link ClaimStatusValidationStep}), so every later step may safely assume the
 * before-state is present.
 */
@Slf4j
@Component
public class BeforeStatePresenceValidationStep implements ClaimAmendmentValidationStep {

  @Override
  public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state) {
    if (state.getBeforeState() != null) {
      return List.of();
    }

    log.error(
        "Amendment before-state snapshot was absent for an existing claim; "
            + "rejecting amendment request as a technical error.");
    return List.of(
        ClaimAmendmentValidationError.of(
            ClaimAmendmentValidationCode.TECHNICAL_ERROR_MISSING_CLAIM_STATE));
  }
}
