package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import java.math.BigDecimal;
import org.openapitools.jackson.nullable.JsonNullable;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;

/**
 * Shared fixtures for the amendment integration tests.
 *
 * <p>Holds the governed reference codes seeded by Flyway migration V41, a valid submitting user and
 * the common "valid, non-pricing amendment" payload, so the behaviourally-distinct amendment
 * integration tests ({@code AmendmentValidationGateIntegrationTest}, {@code
 * AmendmentCommitRollbackIntegrationTest} and {@code
 * ClaimAmendmentPersistenceServiceIntegrationTest}) do not each re-declare them.
 */
public final class AmendmentTestFixtures {

  /** Governed requested-by code seeded by Flyway migration V41. */
  public static final String REQUESTED_BY_PROVIDER = "PROVIDER";

  /** Governed amendment-reason code (valid under {@link #REQUESTED_BY_PROVIDER}) seeded by V41. */
  public static final String REASON_PROVIDER_ERROR = "PROVIDER_ERROR";

  /** A structurally valid submitting-user UUID. */
  public static final String VALID_USER_UUID = "0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e";

  /** An amended fee code used to prove the claim's provider-entered values are written. */
  public static final String AMENDED_FEE_CODE = "AMENDED_FEE_CODE";

  /** An amended claim-summary-fee net profit costs amount. */
  public static final BigDecimal AMENDED_NET_PROFIT_COSTS = new BigDecimal("99.99");

  private AmendmentTestFixtures() {}

  /**
   * A valid, non-pricing amendment payload: requested-by, reason and user id all valid, with an
   * amended fee code so a successful amendment has a provider-entered change to apply.
   *
   * @return the amendment payload
   */
  public static ClaimAmendmentPayload validPayload() {
    return ClaimAmendmentPayload.builder()
        .amendmentRequestedBy(JsonNullable.of(REQUESTED_BY_PROVIDER))
        .amendmentReasonCode(JsonNullable.of(REASON_PROVIDER_ERROR))
        .amendmentUserId(JsonNullable.of(VALID_USER_UUID))
        .feeCode(JsonNullable.of(AMENDED_FEE_CODE))
        .build();
  }
}
