package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import jakarta.persistence.OptimisticLockException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;

/**
 * Proves the true commit-time rollback: when the claim is modified concurrently between the prepare
 * and commit phases, the {@code @Version} optimistic-lock guard fails the atomic write and
 * <b>nothing</b> is persisted.
 *
 * <p>Unlike a validation failure (which never opens a write transaction, so there is nothing to
 * roll back), this exercises the one path where a transaction is actually started and then rolled
 * back: {@link ClaimAmendmentCommitService#commit} reattaches the claim read at prepare time via
 * {@link jakarta.persistence.EntityManager#merge}, and a version bumped since then raises {@link
 * jakarta.persistence.OptimisticLockException} (mapped to HTTP 409 by {@code
 * DataClaimsExceptionHandler}), discarding the amendment insert and the claim writes.
 *
 * <p>The concurrent modification is injected as a side effect of the (otherwise passing) validation
 * phase - which runs with no held transaction - by committing an update to the claim row in its own
 * transaction. This reproduces a real racing writer landing between prepare and commit. The test is
 * deliberately <b>not</b> {@code @Transactional}: the concurrent update must commit independently
 * and be visible to the commit transaction, and the commit must own its own transaction so its
 * rollback is genuine (not masked by an outer test transaction). {@link AbstractIntegrationTest}
 * clears all data before each test, so the committed rows do not leak.
 */
@DisplayName("Amendment rolls back the commit on a concurrent modification")
class AmendmentCommitRollbackIntegrationTest extends AbstractIntegrationTest {

  // Governed reference codes seeded by Flyway migration V41, and a valid submitting user.
  private static final String REQUESTED_BY_PROVIDER = "PROVIDER";
  private static final String REASON_PROVIDER_ERROR = "PROVIDER_ERROR";
  private static final String VALID_USER_UUID = "0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e";
  private static final String AMENDED_FEE_CODE = "AMENDED_FEE_CODE";

  @Autowired private ClaimAmendmentPreparationService preparationService;
  @Autowired private ClaimAmendmentCommitService commitService;

  @BeforeEach
  void setUp() {
    seedClaimsData();
    claimAmendmentRepository.deleteAll();
    // Put the claim in an amendable state and commit it (this is the version the prepare phase
    // reads).
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    claim.setStatus(ClaimStatus.VALID);
    claimRepository.saveAndFlush(claim);
  }

  @Test
  @DisplayName("concurrent modification during validation fails the commit and persists nothing")
  void concurrentModificationRollsBackTheAtomicWrite() {
    long amendmentsBefore = claimAmendmentRepository.count();
    String originalFeeCode = claimRepository.findById(CLAIM_1_ID).orElseThrow().getFeeCode();

    // A single "validation step" that passes but, as a side effect, commits a concurrent update to
    // the claim row (bumping its @Version) - simulating a racing writer between prepare and commit.
    ClaimAmendmentValidationStep concurrentModifier =
        state -> {
          Claim racing = claimRepository.findById(CLAIM_1_ID).orElseThrow();
          racing.setLineNumber(racing.getLineNumber() + 1);
          claimRepository.saveAndFlush(racing);
          return List.of();
        };

    ClaimAmendmentService service =
        new ClaimAmendmentService(
            preparationService,
            new ClaimAmendmentValidationService(concurrentModifier),
            commitService);

    // Hibernate detects the stale @Version at merge/flush and raises the JPA
    // OptimisticLockException
    // (mapped to HTTP 409 by DataClaimsExceptionHandler). The commit transaction is rolled back.
    assertThatThrownBy(() -> service.submitAmendment(CLAIM_1_ID, validPayload()))
        .isInstanceOf(OptimisticLockException.class);

    // The commit transaction rolled back: no amendment row, and the amendment's claim writes
    // (e.g. the fee-code change and the amended flag) were discarded.
    assertThat(claimAmendmentRepository.count()).isEqualTo(amendmentsBefore);
    Claim reloaded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(reloaded.isAmended()).isFalse();
    assertThat(reloaded.getFeeCode()).isEqualTo(originalFeeCode);
  }

  private ClaimAmendmentPayload validPayload() {
    return ClaimAmendmentPayload.builder()
        .amendmentRequestedBy(JsonNullable.of(REQUESTED_BY_PROVIDER))
        .amendmentReasonCode(JsonNullable.of(REASON_PROVIDER_ERROR))
        .amendmentUserId(JsonNullable.of(VALID_USER_UUID))
        .feeCode(JsonNullable.of(AMENDED_FEE_CODE))
        .build();
  }
}
