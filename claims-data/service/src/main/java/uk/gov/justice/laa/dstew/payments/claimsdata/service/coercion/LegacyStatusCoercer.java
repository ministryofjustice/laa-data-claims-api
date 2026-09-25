package uk.gov.justice.laa.dstew.payments.claimsdata.service.coercion;

import lombok.extern.slf4j.Slf4j;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimAmendmentPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;

/**
 * TEMPORARY (DSTEW-2173): the legacy-status coercion implementation. Rewrites {@code
 * VALIDATED_PENDING_APPROVAL} to the legacy "accepted" status until the validated-pending-approval
 * lifecycle is enabled in every environment, and logs each coercion so the rewrite is observable.
 *
 * <p>{@code StatusCoercerConfig} selects this implementation unless the feature property is an
 * explicit {@code true}. Invalid and missing values therefore retain the safe legacy behaviour.
 *
 * <p>To remove once the validated-pending-approval lifecycle no longer needs gating: delete this
 * class (see {@link StatusCoercer} for the full removal checklist).
 */
@Slf4j
class LegacyStatusCoercer implements StatusCoercer {

  @Override
  public void coerce(SubmissionPatch patch) {
    if (patch != null && patch.getStatus() == SubmissionStatus.VALIDATED_PENDING_APPROVAL) {
      log.warn(
          "Validated-pending-approval lifecycle disabled: coercing submission status "
              + "VALIDATED_PENDING_APPROVAL -> VALIDATION_SUCCEEDED");
      patch.setStatus(SubmissionStatus.VALIDATION_SUCCEEDED);
    }
  }

  @Override
  public void coerce(BulkSubmissionPatch patch) {
    if (patch != null && patch.getStatus() == BulkSubmissionStatus.VALIDATED_PENDING_APPROVAL) {
      log.warn(
          "Validated-pending-approval lifecycle disabled: coercing bulk submission status "
              + "VALIDATED_PENDING_APPROVAL -> VALIDATION_SUCCEEDED");
      patch.setStatus(BulkSubmissionStatus.VALIDATION_SUCCEEDED);
    }
  }

  @Override
  public void coerce(ClaimAmendmentPatch patch) {
    if (patch != null && patch.getStatus() == ClaimStatus.VALIDATED_PENDING_APPROVAL) {
      log.warn(
          "Validated-pending-approval lifecycle disabled: coercing claim status "
              + "VALIDATED_PENDING_APPROVAL -> VALID");
      patch.setStatus(ClaimStatus.VALID);
    }
  }

  @Override
  public void coerce(Submission submission) {
    if (submission != null
        && submission.getStatus() == SubmissionStatus.VALIDATED_PENDING_APPROVAL) {
      log.warn(
          "Validated-pending-approval lifecycle disabled: coercing NIL submission status "
              + "VALIDATED_PENDING_APPROVAL -> VALIDATION_SUCCEEDED for submission id [{}]",
          submission.getId());
      submission.setStatus(SubmissionStatus.VALIDATION_SUCCEEDED);
    }
  }
}
