package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.AMENDED_FEE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.AMENDED_NET_PROFIT_COSTS;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.REASON_PROVIDER_ERROR;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.REQUESTED_BY_PROVIDER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;

/**
 * Integration test for {@link ClaimAmendmentPersistenceService}: persists the business record of a
 * successful (non-pricing) amendment end to end and asserts the {@code claim_amendment} row, the
 * computed diff and the applied claim and claim-summary-fee column writes.
 */
class ClaimAmendmentPersistenceServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired private ClaimAmendmentPersistenceService persistenceService;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    seedClaimsData();
    claimAmendmentRepository.deleteAll();
  }

  @Test
  @Transactional
  @DisplayName("persists one claim_amendment row and applies the amended claim and fee values")
  void persistsSuccessfulAmendment() {
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();

    ClaimStateSnapshot before =
        ClaimStateSnapshot.builder()
            .claimId(CLAIM_1_ID)
            .lineNumber(claim.getLineNumber())
            .matterTypeCode(claim.getMatterTypeCode())
            .feeCode(claim.getFeeCode())
            .netProfitCostsAmount(new BigDecimal("10.00"))
            .build();
    ClaimStateSnapshot post =
        before.toBuilder()
            .feeCode(AMENDED_FEE_CODE)
            .netProfitCostsAmount(AMENDED_NET_PROFIT_COSTS)
            .build();

    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .amendmentRequestedBy(JsonNullable.of(REQUESTED_BY_PROVIDER))
            .amendmentReasonCode(JsonNullable.of(REASON_PROVIDER_ERROR))
            .amendmentUserId(JsonNullable.of(ClaimsDataTestUtil.USER_ID))
            .feeCode(JsonNullable.of(AMENDED_FEE_CODE))
            .netProfitCostsAmount(JsonNullable.of(AMENDED_NET_PROFIT_COSTS))
            .build();

    ClaimAmendmentState state =
        ClaimAmendmentState.builder()
            .beforeState(before)
            .requestPayload(payload)
            .postAmendmentState(post)
            .build();

    ClaimAmendment saved = persistenceService.persistSuccessfulAmendment(claim, state);

    // Flush the inserted amendment and the dirty (mutated) managed entities, then re-read.
    entityManager.flush();
    entityManager.clear();

    ClaimAmendment reloaded = claimAmendmentRepository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getRequestedByCode()).isEqualTo(REQUESTED_BY_PROVIDER);
    assertThat(reloaded.getAmendmentReasonCode()).isEqualTo(REASON_PROVIDER_ERROR);
    assertThat(reloaded.getCreatedByUserId()).isEqualTo(ClaimsDataTestUtil.USER_ID);
    assertThat(reloaded.getCreatedOn()).isNotNull();
    assertThat(reloaded.getBeforeState()).contains("feeCode");
    assertThat(reloaded.getRequestPayload()).contains(AMENDED_FEE_CODE);
    // The diff is computed now from the before-vs-post (tri-state payload) comparison. Note the
    // JSONB column is re-serialised by PostgreSQL (keys spaced), so assert on stable substrings.
    assertThat(reloaded.getDiff())
        .contains("schema_version")
        .contains("claim.feeCode")
        .contains("claimSummaryFee.netProfitCostsAmount");

    Claim reloadedClaim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(reloadedClaim.getFeeCode()).isEqualTo(AMENDED_FEE_CODE);
    assertThat(reloadedClaim.isAmended()).isTrue();

    ClaimSummaryFee reloadedFee = claimSummaryFeeRepository.findByClaimId(CLAIM_1_ID).orElseThrow();
    assertThat(reloadedFee.getNetProfitCostsAmount())
        .isEqualByComparingTo(AMENDED_NET_PROFIT_COSTS);
  }
}
