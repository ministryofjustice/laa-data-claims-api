package uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection;

import java.util.UUID;

/** This interface provides a Spring Data projection from the compound query. */
public interface ClaimWarningCountProjection {
  UUID getClaimId();

  long getWarningCount();
}
