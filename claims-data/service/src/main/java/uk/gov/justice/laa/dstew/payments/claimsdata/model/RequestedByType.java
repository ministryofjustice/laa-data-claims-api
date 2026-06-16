package uk.gov.justice.laa.dstew.payments.claimsdata.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Enum holding Requested by Types. */
@Getter
@RequiredArgsConstructor
public enum RequestedByType {
  PROVIDER("Provider Initiated"),
  AUDITOR("Auditor Remediated");

  private final String displayLabel;
}
