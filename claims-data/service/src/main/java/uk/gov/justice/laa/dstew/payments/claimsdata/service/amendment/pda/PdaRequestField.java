package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.pda;

import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClaimFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;

/**
 * Determines whether a given claim field could influence a downstream Provider Details API (PDA)
 * {@code getProviderFirmSchedules} request.
 *
 * <p>The field names are the stable diff identifiers emitted by {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.AmendmentChangeDetector}
 * (e.g. {@code "claim.feeCode"}), so this check speaks exactly the vocabulary carried by an
 * amendment {@link uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry}.
 *
 * <p>The PDA request is built from three values:
 *
 * <ul>
 *   <li>{@code officeCode} — sourced from the submission's {@code officeAccountNumber}. This is
 *       read-only submission context, not a provider-amendable claim field, so it can never appear
 *       in an amendment diff and is therefore out of scope for this amendment-triggered decision.
 *   <li>{@code effectiveDate} — derived from a claim using a strict priority order: {@code PROD}
 *       fee with a {@code caseConcludedDate}, otherwise {@code caseStartDate}, otherwise {@code
 *       representationOrderDate}, otherwise a date derived from {@code uniqueFileNumber}.
 *   <li>{@code requireOpenStatus} — a hardcoded {@code false}, influenced by nothing.
 * </ul>
 *
 * <p>Because {@code effectiveDate} is priority-based, a lower-priority field only affects the
 * request when no higher-priority field is populated. The fully-merged effective values are read
 * from {@link ClaimStateSnapshot}, so the priority is evaluated against the claim's resolved state
 * rather than a sparse amendment delta.
 *
 * <p>{@code feeCode} is treated as always impacting the request: in addition to contributing to the
 * {@code effectiveDate} priority, {@code feeCode}-derived data is later compared against the PDA
 * response to determine a validation value. Any change to {@code feeCode} therefore affects the
 * requirement to use the PDA, regardless of the other fields.
 */
public final class PdaRequestField {

  private static final String PROD_FEE_CODE = "PROD";

  private PdaRequestField() {
    // Utility class; no instances.
  }

  /**
   * Indicates whether the named field could influence the PDA {@code getProviderFirmSchedules}
   * request, given the claim's fully-merged effective state.
   *
   * @param fieldName the diff field identifier being checked (e.g. {@code "claim.feeCode"})
   * @param mergedStateSnapshot the post-amendment (effective) claim snapshot
   * @return {@code true} if the field could influence the request, otherwise {@code false}
   */
  public static boolean impactsPda(String fieldName, ClaimStateSnapshot mergedStateSnapshot) {

    boolean prodWithConcluded =
        PROD_FEE_CODE.equals(mergedStateSnapshot.getFeeCode())
            && mergedStateSnapshot.getCaseConcludedDate() != null;

    return switch (fieldName) {
      case ClaimFields.FEE_CODE -> true;
      case ClaimFields.CASE_CONCLUDED_DATE ->
          PROD_FEE_CODE.equals(mergedStateSnapshot.getFeeCode());
      case ClaimFields.CASE_START_DATE -> !prodWithConcluded;
      case ClaimFields.REPRESENTATION_ORDER_DATE ->
          !prodWithConcluded && mergedStateSnapshot.getCaseStartDate() == null;
      case ClaimFields.UNIQUE_FILE_NUMBER ->
          !prodWithConcluded
              && mergedStateSnapshot.getCaseStartDate() == null
              && mergedStateSnapshot.getRepresentationOrderDate() == null;
      default -> false;
    };
  }
}
