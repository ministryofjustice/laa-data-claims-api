package uk.gov.justice.laa.dstew.payments.claimsdata.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Enum holding Amendment Reason Types. */
@Getter
@RequiredArgsConstructor
public enum AmendmentReasonType {

  // Provider Invariants
  TYPING_ERROR(RequestedByType.PROVIDER, "Typing Error"),
  // Auditor Invariants
  COMPLIANCE_CORRECTION(RequestedByType.AUDITOR, "Compliance Correction");

  private final RequestedByType requestedBy;
  private final String displayLabel;
}
