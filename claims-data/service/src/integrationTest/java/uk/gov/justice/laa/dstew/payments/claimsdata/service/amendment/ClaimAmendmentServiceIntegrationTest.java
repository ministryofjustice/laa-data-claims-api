package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * End-to-end integration test for {@link ClaimAmendmentService}: drives the real orchestration
 * (retrieve {@literal ->} validate {@literal ->} persist) against a real PostgreSQL
 * (Testcontainers) and the Flyway-seeded reference data (V41), proving the atomic-save behaviour of
 * DSTEW-1771 for the currently-available flow (non-pricing amendment, no PDA/FSP).
 *
 * <p>The class is transactional so all writes roll back after each test, leaving the seed
 * untouched.
 */
class ClaimAmendmentServiceIntegrationTest extends AbstractIntegrationTest {

  // Governed reference codes seeded by Flyway migration V41.
  private static final String REQUESTED_BY_PROVIDER = "PROVIDER";
  private static final String REASON_PROVIDER_ERROR = "PROVIDER_ERROR";
  private static final String VALID_USER_UUID = "0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e";
  private static final String AMENDED_FEE_CODE = "AMENDED_FEE_CODE";

  @Autowired private ClaimAmendmentService amendmentService;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    seedClaimsData();
    claimAmendmentRepository.deleteAll();
  }

  @Test
  @Transactional
  @DisplayName("valid non-pricing amendment is retrieved, validated and persisted atomically")
  void submitsValidAmendmentAtomically() {
    // Ensure the claim is in an amendable state for the status gate.
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    claim.setStatus(ClaimStatus.VALID);

    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .amendmentRequestedBy(JsonNullable.of(REQUESTED_BY_PROVIDER))
            .amendmentReasonCode(JsonNullable.of(REASON_PROVIDER_ERROR))
            .amendmentUserId(JsonNullable.of(VALID_USER_UUID))
            .feeCode(JsonNullable.of(AMENDED_FEE_CODE))
            .build();

    ClaimAmendmentResult result = amendmentService.submitAmendment(CLAIM_1_ID, payload);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.errors()).isEmpty();

    // Flush the amendment insert and the dirty (mutated) managed claim, then re-read.
    entityManager.flush();
    entityManager.clear();

    ClaimAmendment reloaded =
        claimAmendmentRepository.findById(result.amendment().getId()).orElseThrow();
    assertThat(reloaded.getRequestedByCode()).isEqualTo(REQUESTED_BY_PROVIDER);
    assertThat(reloaded.getAmendmentReasonCode()).isEqualTo(REASON_PROVIDER_ERROR);
    assertThat(reloaded.getDiff()).contains("schema_version").contains("claim.feeCode");

    Claim reloadedClaim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(reloadedClaim.getFeeCode()).isEqualTo(AMENDED_FEE_CODE);
    assertThat(reloadedClaim.isAmended()).isTrue();
  }

  @Test
  @Transactional
  @DisplayName("invalid amendment is rejected with errors and nothing is persisted")
  void rejectsInvalidAmendmentAndPersistsNothing() {
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    claim.setStatus(ClaimStatus.VALID);

    long amendmentsBefore = claimAmendmentRepository.count();

    // Missing Requested By and Amendment Reason, and a malformed user id: all non-fatal errors.
    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .amendmentUserId(JsonNullable.of("not-a-uuid"))
            .feeCode(JsonNullable.of(AMENDED_FEE_CODE))
            .build();

    ClaimAmendmentResult result = amendmentService.submitAmendment(CLAIM_1_ID, payload);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.errors()).isNotEmpty();

    entityManager.flush();
    entityManager.clear();

    assertThat(claimAmendmentRepository.count()).isEqualTo(amendmentsBefore);
    Claim reloadedClaim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(reloadedClaim.isAmended()).isFalse();
    assertThat(reloadedClaim.getFeeCode()).isNotEqualTo(AMENDED_FEE_CODE);
  }
}
