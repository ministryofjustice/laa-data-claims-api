package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import java.util.Objects;
import lombok.experimental.UtilityClass;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.DerivedClaimStatus;

/**
 * Single source of truth for deriving a {@link DerivedClaimStatus} from a claim's raw {@link
 * ClaimStatus} together with its {@code hasAssessment} and {@code isAmended} flags.
 *
 * <p>The derivation precedence is fixed business logic (evaluated top-to-bottom, first match wins,
 * <em>not</em> chronological):
 *
 * <ol>
 *   <li>{@code claim_status = VOID} &rarr; {@link DerivedClaimStatus#VOIDED}
 *   <li>{@code claim_status = INVALID} &rarr; {@link DerivedClaimStatus#INVALID}
 *   <li>{@code claim_status = READY_TO_PROCESS} &rarr; {@link DerivedClaimStatus#READY_TO_PROCESS}
 *   <li>{@code has_assessment = true} &rarr; {@link DerivedClaimStatus#ASSESSED}
 *   <li>{@code is_amended = true} &rarr; {@link DerivedClaimStatus#AMENDED}
 *   <li>otherwise (i.e. {@code claim_status = VALID}) &rarr; {@link DerivedClaimStatus#ACCEPTED}
 * </ol>
 *
 * <p>This class is the authoritative Java implementation of the precedence rules. The SQL {@code
 * CASE} expression used for sorting (see {@code ClaimSpecification.orderByDerivedClaimStatus}) must
 * mirror this precedence and is guarded by a parity test so the two encodings cannot silently
 * diverge. The canonical ordering itself lives in the {@link DerivedClaimStatus} enum declaration
 * order and must not be duplicated elsewhere.
 *
 * <p>See {@code docs/derived-claim-status.md} for the full specification and truth table.
 */
@UtilityClass
public class DerivedClaimStatusResolver {

  /**
   * Derives the {@link DerivedClaimStatus} for the supplied source values.
   *
   * @param status the raw claim status; must not be {@code null}
   * @param hasAssessment whether the claim has an associated assessment
   * @param isAmended whether the claim has been amended
   * @return the derived business status
   * @throws NullPointerException if {@code status} is {@code null}
   */
  public static DerivedClaimStatus resolve(
      ClaimStatus status, boolean hasAssessment, boolean isAmended) {
    Objects.requireNonNull(status, "claim_status must not be null");
    return switch (status) {
      case VOID -> DerivedClaimStatus.VOIDED;
      case INVALID -> DerivedClaimStatus.INVALID;
      case READY_TO_PROCESS -> DerivedClaimStatus.READY_TO_PROCESS;
      case VALID -> {
        if (hasAssessment) {
          yield DerivedClaimStatus.ASSESSED;
        }
        if (isAmended) {
          yield DerivedClaimStatus.AMENDED;
        }
        yield DerivedClaimStatus.ACCEPTED;
      }
    };
  }

  /**
   * Null-tolerant overload: a {@code null} {@code hasAssessment} or {@code isAmended} is treated as
   * {@code false}.
   *
   * @param status the raw claim status; must not be {@code null}
   * @param hasAssessment whether the claim has an associated assessment ({@code null} =&gt; false)
   * @param isAmended whether the claim has been amended ({@code null} =&gt; false)
   * @return the derived business status
   */
  public static DerivedClaimStatus resolve(
      ClaimStatus status, Boolean hasAssessment, Boolean isAmended) {
    return resolve(status, Boolean.TRUE.equals(hasAssessment), Boolean.TRUE.equals(isAmended));
  }
}
