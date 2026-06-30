package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.pda;

import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;

/**
 * Determines whether a given claim field could influence a downstream Provider Details API (PDA)
 * {@code getProviderFirmSchedules} request.
 *
 * <p>The PDA request is built from three values:
 *
 * <ul>
 *   <li>{@code officeCode} — sourced directly from the submission's {@code officeAccountNumber}, so
 *       it always affects the request.
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
   * @param fieldName the claim-side field name being checked
   * @param mergedStateSnapshot the post-amendment (effective) claim snapshot
   * @return {@code true} if the field could influence the request, otherwise {@code false}
   */
  public static boolean affectsPdaRequest(String fieldName, ClaimStateSnapshot mergedStateSnapshot) {

    boolean prodWithConcluded =
        PROD_FEE_CODE.equals(mergedStateSnapshot.getFeeCode()) && mergedStateSnapshot.getCaseConcludedDate() != null;

    return switch (fieldName) {
      case "officeAccountNumber" -> true;
      case "feeCode" -> mergedStateSnapshot.getCaseConcludedDate() != null;
      case "caseConcludedDate" -> PROD_FEE_CODE.equals(mergedStateSnapshot.getFeeCode());
      case "caseStartDate" -> !prodWithConcluded;
      case "representationOrderDate" -> !prodWithConcluded && mergedStateSnapshot.getCaseStartDate() == null;
      case "uniqueFileNumber" ->
          !prodWithConcluded
              && mergedStateSnapshot.getCaseStartDate() == null
              && mergedStateSnapshot.getRepresentationOrderDate() == null;
      default -> false;
    };
  }
}
