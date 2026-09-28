package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ValidationMessageLogFixtures.FSP_SOURCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ValidationMessageLogFixtures.currentFspWarning;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.OptimisticLockException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationIssue;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationSeverity;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

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

    // Simulate a concurrent writer committing a change first, advancing the row to version 1.
    Claim concurrent = claimRepository.findById(claimId).orElseThrow();
    concurrent.setCaseReferenceNumber("BUMPED-BY-CONCURRENT-WRITER");
    claimRepository.saveAndFlush(concurrent);
    assertThat(claimRepository.findById(claimId).orElseThrow().getVersion()).isEqualTo(1L);

    // Act & Assert: committing the stale (version 0) instance must fail the version check. The
    // guard catches it as a JPA OptimisticLockException and rethrows - proving the catch type is
    // correct.
    //
    // Reusing `seeded` here is deliberate: it is the detached, version-0 snapshot the
    // commit will reattach, mirroring a claim snapshotted at prepare time.
    assertThatThrownBy(() -> commitService.commit(seeded, ClaimAmendmentState.builder().build()))
        .isInstanceOf(OptimisticLockException.class);

    // The structured WARN carries only the safe fields on a single log event. Asserting per-event
    // (rather than concatenating all WARNs) prevents a false positive where separate WARNs happen
    // to collectively contain every substring.
    assertThat(logAppender.list)
        .filteredOn(event -> event.getLevel() == Level.WARN)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(
            msg ->
                assertThat(msg)
                    .contains("event=CLAIM_VERSION_CONFLICT")
                    .contains("claimId=" + claimId)
                    .contains("submittedClaimVersion=0")
                    .contains("conflictPoint=final_save"));

    // The whole commit rolled back: no audit row, and the claim keeps the concurrent writer's state
    // (version 1, bumped reference) rather than the amendment.
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(claimId)).isEmpty();
    Claim reloaded = claimRepository.findById(claimId).orElseThrow();
    assertThat(reloaded.getVersion()).isEqualTo(1L);
    assertThat(reloaded.isAmended()).isFalse();
    assertThat(reloaded.getCaseReferenceNumber()).isEqualTo("BUMPED-BY-CONCURRENT-WRITER");
  }

  @Test
  @DisplayName(
      "a repricing-style commit conflict leaves current warnings unchanged and persists no replacement warnings")
  void concurrentVersionBumpLeavesWarningStateUntouched() {
    seedSubmissionsData();
    Claim seeded =
        claimRepository.saveAndFlush(
            Claim.builder()
                .id(Uuid7.timeBasedUuid())
                .submission(submissionRepository.getReferenceById(SUBMISSION_1_ID))
                .status(ClaimStatus.VALID)
                .lineNumber(1)
                .caseReferenceNumber("CONFLICT-WARN-CRN")
                .feeCode("FEE01")
                .matterTypeCode("MTC")
                .createdByUserId(CREATED_BY)
                .createdOn(CREATED_ON)
                .build());
    UUID claimId = seeded.getId();

    ValidationMessageLog seededWarning =
        currentFspWarning(SUBMISSION_1_ID, claimId, seeded.getVersion(), "current-warning");
    validationMessageLogRepository.saveAndFlush(seededWarning);

    Claim concurrent = claimRepository.findById(claimId).orElseThrow();
    concurrent.setCaseReferenceNumber("BUMPED-BEFORE-WARNING-PERSIST");
    claimRepository.saveAndFlush(concurrent);

    ClaimAmendmentState state =
        ClaimAmendmentState.builder().fspResponseContext(new FeeCalculationResponse()).build();
    state.addWarnings(
        List.of(
            ValidationIssue.builder()
                .code("WAR001")
                .message("replacement-warning")
                .severity(ValidationSeverity.WARNING)
                .build()));

    assertThatThrownBy(() -> commitService.commit(seeded, state))
        .isInstanceOf(OptimisticLockException.class);

    List<ValidationMessageLog> warnings =
        validationMessageLogRepository.findAll().stream()
            .filter(log -> claimId.equals(log.getClaimId()))
            .filter(log -> log.getType() == ValidationMessageType.WARNING)
            .filter(log -> FSP_SOURCE.equals(log.getSource()))
            .toList();

    assertThat(warnings).hasSize(1);
    assertThat(warnings.getFirst().getId()).isEqualTo(seededWarning.getId());
    assertThat(warnings.getFirst().getDisplayMessage()).isEqualTo("current-warning");
    assertThat(warnings.getFirst().getSupersededByVersion())
        .isEqualTo(ValidationMessageLogRepository.CURRENT_SUPERSEDED_BY_VERSION);
    assertThat(
            validationMessageLogRepository.countAllByClaimIdAndType(
                claimId, ValidationMessageType.WARNING))
        .isEqualTo(1);
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(claimId)).isEmpty();
  }
}
