package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.OptimisticLockException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Real-database integration coverage for the optimistic-lock guard in {@link
 * ClaimAmendmentCommitService#commit}.
 *
 * <p>The commit step is {@code @Transactional(REQUIRES_NEW)} and reattaches the detached, validated
 * {@link Claim} via {@code merge} followed by an explicit {@code flush}. The flush forces the
 * versioned {@code UPDATE} to execute <em>inside</em> the method so that a concurrent modification
 * raises the conflict within the guard's {@code try/catch} - rather than later at transaction
 * commit, where the structured {@code conflictPoint=final_save} warning would be missed.
 *
 * <p>This test exercises that path against a real PostgreSQL (Testcontainers) and real Hibernate to
 * prove three things the mock-based unit test cannot:
 *
 * <ul>
 *   <li>a genuine {@code @Version} collision during the commit is detected and rethrown;
 *   <li>the structured WARN is emitted with the safe fields only; and
 *   <li>the whole write rolls back - no {@code claim_amendment} row is written and the claim is not
 *       marked amended.
 * </ul>
 *
 * <p>It also pins down the concrete exception type that Hibernate raises for a version conflict, so
 * the guard's {@code catch (OptimisticLockException)} is verified against real behaviour. If a
 * future change caused the conflict to surface as a different type (e.g. Spring's {@code
 * ObjectOptimisticLockingFailureException}) this test would fail, flagging that the catch needs
 * broadening.
 *
 * <p>The class is intentionally <b>not</b> {@code @Transactional}: the seed claim and the simulated
 * concurrent version bump must be committed so the {@code REQUIRES_NEW} commit transaction can see
 * them. {@link AbstractIntegrationTest} clears all tables before each test, so nothing leaks.
 */
@DisplayName("ClaimAmendmentCommitService optimistic-lock guard integration test")
class ClaimAmendmentCommitConflictIntegrationTest extends AbstractIntegrationTest {

  private static final String CREATED_BY = "amendment-commit-conflict-integration-test";

  @Autowired private ClaimAmendmentCommitService commitService;

  private ListAppender<ILoggingEvent> logAppender;
  private Logger serviceLogger;

  @BeforeEach
  void attachLogAppender() {
    serviceLogger = (Logger) LoggerFactory.getLogger(ClaimAmendmentCommitService.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    serviceLogger.addAppender(logAppender);
  }

  @AfterEach
  void detachLogAppender() {
    serviceLogger.detachAppender(logAppender);
  }

  @Test
  @DisplayName(
      "a concurrent version bump makes the commit fail the optimistic-lock check: it logs the "
          + "final_save conflict, rethrows, and persists nothing")
  void concurrentVersionBumpIsDetectedLoggedAndRolledBack() {
    // Arrange: seed a submission and an amendable claim, committed at version 0.
    seedSubmissionsData();
    Claim seeded =
        claimRepository.saveAndFlush(
            Claim.builder()
                .id(Uuid7.timeBasedUuid())
                .submission(submissionRepository.getReferenceById(SUBMISSION_1_ID))
                .status(ClaimStatus.VALID)
                .lineNumber(1)
                .caseReferenceNumber("CONFLICT-CRN")
                .feeCode("FEE01")
                .matterTypeCode("MTC")
                .createdByUserId(CREATED_BY)
                .createdOn(CREATED_ON)
                .build());
    UUID claimId = seeded.getId();
    assertThat(seeded.getVersion()).isZero();

    // The detached, version-0 instance is what the commit will reattach - mirroring a claim
    // snapshotted at prepare time.
    Claim staleClaim = seeded;

    // Simulate a concurrent writer committing a change first, advancing the row to version 1.
    Claim concurrent = claimRepository.findById(claimId).orElseThrow();
    concurrent.setCaseReferenceNumber("BUMPED-BY-CONCURRENT-WRITER");
    claimRepository.saveAndFlush(concurrent);
    assertThat(claimRepository.findById(claimId).orElseThrow().getVersion()).isEqualTo(1L);

    // Act & Assert: committing the stale (version 0) claim must fail the version check. The guard
    // catches it as a JPA OptimisticLockException and rethrows - proving the catch type is correct.
    assertThatThrownBy(
            () -> commitService.commit(staleClaim, ClaimAmendmentState.builder().build()))
        .isInstanceOf(OptimisticLockException.class);

    // The structured WARN carries only the safe fields.
    String formatted =
        logAppender.list.stream()
            .filter(e -> e.getLevel() == Level.WARN)
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + b);
    assertThat(formatted)
        .contains("event=CLAIM_VERSION_CONFLICT")
        .contains("claimId=" + claimId)
        .contains("submittedClaimVersion=0")
        .contains("conflictPoint=final_save");

    // The whole commit rolled back: no audit row, and the claim keeps the concurrent writer's state
    // (version 1, bumped reference) rather than the amendment.
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(claimId)).isEmpty();
    Claim reloaded = claimRepository.findById(claimId).orElseThrow();
    assertThat(reloaded.getVersion()).isEqualTo(1L);
    assertThat(reloaded.isAmended()).isFalse();
    assertThat(reloaded.getCaseReferenceNumber()).isEqualTo("BUMPED-BY-CONCURRENT-WRITER");
  }
}
