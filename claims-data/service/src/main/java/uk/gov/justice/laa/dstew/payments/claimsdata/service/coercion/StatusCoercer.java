package uk.gov.justice.laa.dstew.payments.claimsdata.service.coercion;

import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimAmendmentPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;

/**
 * TEMPORARY (DSTEW-2173): translates the {@code VALIDATED_PENDING_APPROVAL} status back to the
 * legacy "accepted" status on inbound writes, so the API can be deployed while the front-end,
 * reporting service and other dependent systems become ready to understand the new status.
 *
 * <p>This is the single choke point for that translation. Every API path that persists a status
 * originating from INITIAL validation funnels its carrier (patch or entity) through one of the
 * overloads below; each overload demotes {@code VALIDATED_PENDING_APPROVAL} to the legacy status
 * for that entity type:
 *
 * <ul>
 *   <li>submission / bulk submission &rarr; {@code VALIDATION_SUCCEEDED}
 *   <li>claim &rarr; {@code VALID}
 * </ul>
 *
 * <p>Because submission coercion happens on the {@link SubmissionPatch} <em>before</em> it is
 * mapped onto the entity and before the event-dispatch reads it, coercing to {@code
 * VALIDATION_SUCCEEDED} automatically re-routes to the existing {@code
 * SUBMISSION_VALIDATION_SUCCEEDED} publication and suppresses the new {@code
 * INITIAL_SUBMISSION_VALIDATION_SUCCEEDED} event &mdash; i.e. the legacy lifecycle continues
 * end-to-end.
 *
 * <p><strong>Default behaviour is legacy coercion</strong>: the validated-pending-approval
 * lifecycle is enabled only when {@code laa.claims.api.validated-pending-approval.enabled=true}.
 * When the property is true, {@code StatusCoercerConfig} falls back to {@link #NO_OP}.
 *
 * <p><strong>Removal, once the validated-pending-approval lifecycle no longer needs
 * gating:</strong> delete the whole {@code service.coercion} package (this interface, {@code
 * LegacyStatusCoercer}, {@code StatusCoercerConfig}), remove the {@code statusCoercer} field and
 * the four {@code statusCoercer.coerce(...)} call sites in {@code SubmissionService}, {@code
 * BulkSubmissionService} and {@code ClaimService}, and drop the {@code
 * laa.claims.api.validated-pending-approval.*} property (plus its Helm wiring). The remaining code
 * is already the desired hold-enabled behaviour. All the removable pieces are greppable by the
 * property name or {@code StatusCoercer}.
 */
public interface StatusCoercer {

  /**
   * Demotes {@code VALIDATED_PENDING_APPROVAL} on the submission patch in place (else leaves it).
   */
  void coerce(SubmissionPatch patch);

  /**
   * Demotes {@code VALIDATED_PENDING_APPROVAL} on the bulk submission patch in place (else leaves
   * it).
   */
  void coerce(BulkSubmissionPatch patch);

  /** Demotes {@code VALIDATED_PENDING_APPROVAL} on the claim patch in place (else leaves it). */
  void coerce(ClaimAmendmentPatch patch);

  /**
   * Demotes {@code VALIDATED_PENDING_APPROVAL} on the submission entity in place (else leaves it).
   * Used by the NIL-create path, which sets the status directly on the entity with no patch
   * involved.
   */
  void coerce(Submission submission);

  /**
   * Selected by {@code StatusCoercerConfig} when the opt-in lifecycle is enabled, so call sites
   * always receive a non-null coercer.
   */
  StatusCoercer NO_OP =
      new StatusCoercer() {
        @Override
        public void coerce(SubmissionPatch patch) {
          // no-op
        }

        @Override
        public void coerce(BulkSubmissionPatch patch) {
          // no-op
        }

        @Override
        public void coerce(ClaimAmendmentPatch patch) {
          // no-op
        }

        @Override
        public void coerce(Submission submission) {
          // no-op
        }
      };
}
