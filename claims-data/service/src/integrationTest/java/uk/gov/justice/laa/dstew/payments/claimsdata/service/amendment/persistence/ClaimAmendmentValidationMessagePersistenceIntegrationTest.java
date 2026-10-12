package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ValidationMessageLogFixtures.FSP_SOURCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ValidationMessageLogFixtures.PDA_SOURCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ValidationMessageLogFixtures.currentFspWarning;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ValidationMessageLogFixtures.currentWarning;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ValidationMessageLogFixtures.supersededFspWarning;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationIssue;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationSeverity;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

/**
 * Persistence-service contract for {@link ClaimAmendmentValidationMessagePersistenceService}: pins
 * the narrow rules that {@code persistCurrentWarnings} must uphold when the amendment commit hands
 * it a managed, already-versioned claim.
 *
 * <p>Each test uses {@code @Transactional} at method level so the test and the service share the
 * same persistence context; explicit {@code entityManager.flush()} / {@code clear()} calls force
 * ordered INSERT/UPDATE emission and drop stale first-level cache entries before assertions read
 * the DB back. This deliberately mirrors the {@code REQUIRES_NEW} commit boundary so the tests fail
 * fast if the service starts to rely on version bookkeeping the commit does not perform.
 *
 * <p>End-to-end pipeline behaviour is covered separately by {@code
 * ClaimAmendmentWarningLifecycleIntegrationTest}.
 */
@DisplayName("ClaimAmendmentValidationMessagePersistenceService integration test")
class ClaimAmendmentValidationMessagePersistenceIntegrationTest extends AbstractIntegrationTest {

  @Autowired private ClaimAmendmentValidationMessagePersistenceService persistenceService;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    seedClaimsData();
  }

  @Test
  @Transactional
  @DisplayName("persists only warning-severity FSP issues for the current managed claim version")
  void persistsOnlyWarningSeverityIssuesForManagedClaimVersion() {
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    long claimVersion = claim.getVersion();

    ClaimAmendmentState state =
        stateWithIssues(warning("WAR001", "warning one"), error("ERR001", "error one"));

    persistenceService.persistCurrentWarnings(claim, state);
    entityManager.flush();
    entityManager.clear();

    List<ValidationMessageLog> logs = warningLogsForClaim(CLAIM_1_ID, FSP_SOURCE);
    assertThat(logs).hasSize(1);
    ValidationMessageLog persisted = logs.getFirst();
    assertThat(persisted.getSource()).isEqualTo(FSP_SOURCE);
    assertThat(persisted.getType()).isEqualTo(ValidationMessageType.WARNING);
    assertThat(persisted.getMessageCode()).isEqualTo("WAR001");
    assertThat(persisted.getDisplayMessage()).isEqualTo("warning one");
    assertThat(persisted.getVersion()).isEqualTo(claimVersion);
    assertThat(persisted.getSupersededByVersion())
        .isEqualTo(ValidationMessageLogRepository.CURRENT_SUPERSEDED_BY_VERSION);
    assertThat(
            validationMessageLogRepository.countAllByClaimIdAndType(
                CLAIM_1_ID, ValidationMessageType.WARNING))
        .isEqualTo(1);
  }

  @Test
  @Transactional
  @DisplayName("supersedes current FSP warnings and preserves historical warnings")
  void supersedesCurrentWarningsAndPreservesHistoricalWarnings() {
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    long seedVersion = claim.getVersion();
    ValidationMessageLog currentSeed =
        currentFspWarning(SUBMISSION_1_ID, CLAIM_1_ID, seedVersion, "current-warning");
    ValidationMessageLog historicalSeed =
        supersededFspWarning(
            SUBMISSION_1_ID, CLAIM_1_ID, seedVersion, seedVersion + 5L, "historical-warning");
    validationMessageLogRepository.saveAll(List.of(currentSeed, historicalSeed));
    claim.setCaseReferenceNumber("SUPERSEDE-CURRENT-WARNINGS");
    entityManager.flush();
    long claimVersion = claim.getVersion();

    persistenceService.persistCurrentWarnings(
        claim, stateWithIssues(warning("WAR002", "replacement")));
    entityManager.flush();
    entityManager.clear();

    List<ValidationMessageLog> claimWarnings = warningLogsForClaim(CLAIM_1_ID, FSP_SOURCE);
    assertThat(claimWarnings).hasSize(3);
    assertThat(claimWarnings)
        .filteredOn(log -> log.getDisplayMessage().equals("current-warning"))
        .singleElement()
        .extracting(ValidationMessageLog::getSupersededByVersion)
        .isEqualTo(claimVersion);
    assertThat(claimWarnings)
        .filteredOn(log -> log.getDisplayMessage().equals("historical-warning"))
        .singleElement()
        .extracting(ValidationMessageLog::getSupersededByVersion)
        .isEqualTo(seedVersion + 5L);
    assertThat(claimWarnings)
        .filteredOn(log -> log.getDisplayMessage().equals("replacement"))
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.getMessageCode()).isEqualTo("WAR002");
              assertThat(log.getVersion()).isEqualTo(claimVersion);
              assertThat(log.getSupersededByVersion())
                  .isEqualTo(ValidationMessageLogRepository.CURRENT_SUPERSEDED_BY_VERSION);
            });
    assertThat(
            validationMessageLogRepository.countAllByClaimIdAndType(
                CLAIM_1_ID, ValidationMessageType.WARNING))
        .isEqualTo(1);
  }

  @Test
  @Transactional
  @DisplayName("successful repricing with zero warnings leaves no current FSP warnings")
  void zeroWarningResultLeavesNoCurrentWarnings() {
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    long seedVersion = claim.getVersion();
    validationMessageLogRepository.save(
        currentFspWarning(SUBMISSION_1_ID, CLAIM_1_ID, seedVersion, "current-warning"));
    claim.setCaseReferenceNumber("ZERO-WARNING-SUPERSESSION");
    entityManager.flush();
    long claimVersion = claim.getVersion();

    persistenceService.persistCurrentWarnings(claim, stateWithIssues());
    entityManager.flush();
    entityManager.clear();

    List<ValidationMessageLog> claimWarnings = warningLogsForClaim(CLAIM_1_ID, FSP_SOURCE);
    assertThat(claimWarnings).hasSize(1);
    assertThat(claimWarnings.getFirst().getDisplayMessage()).isEqualTo("current-warning");
    assertThat(claimWarnings.getFirst().getSupersededByVersion()).isEqualTo(claimVersion);
    assertThat(
            validationMessageLogRepository.countAllByClaimIdAndType(
                CLAIM_1_ID, ValidationMessageType.WARNING))
        .isZero();
  }

  @Test
  @Transactional
  @DisplayName("does nothing when FSP response context is absent even if warnings were collected")
  void doesNothingWhenFspResponseContextAbsentEvenWithCollectedWarnings() {
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    long seedVersion = claim.getVersion();
    validationMessageLogRepository.save(
        currentFspWarning(SUBMISSION_1_ID, CLAIM_1_ID, seedVersion, "current-warning"));
    entityManager.flush();

    // The guard is the FSP response context, not the presence of warnings. A state that carries
    // warnings but no FSP response must still be a no-op.
    ClaimAmendmentState state = ClaimAmendmentState.builder().build();
    state.addWarnings(List.of(warning("WAR-IGNORED", "should-not-persist")));
    persistenceService.persistCurrentWarnings(claim, state);
    entityManager.flush();
    entityManager.clear();

    List<ValidationMessageLog> claimWarnings = warningLogsForClaim(CLAIM_1_ID, FSP_SOURCE);
    assertThat(claimWarnings).hasSize(1);
    assertThat(claimWarnings.getFirst().getDisplayMessage()).isEqualTo("current-warning");
    assertThat(claimWarnings.getFirst().getSupersededByVersion()).isZero();
    assertThat(
            validationMessageLogRepository.countAllByClaimIdAndType(
                CLAIM_1_ID, ValidationMessageType.WARNING))
        .isEqualTo(1);
  }

  @Test
  @Transactional
  @DisplayName("supersedes only FSP warnings and leaves non-FSP warnings (e.g. PDA) untouched")
  void supersedesOnlyFspWarningsAndLeavesOtherSourcesUntouched() {
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    long seedVersion = claim.getVersion();
    ValidationMessageLog fspSeed =
        currentFspWarning(SUBMISSION_1_ID, CLAIM_1_ID, seedVersion, "fsp-current-warning");
    ValidationMessageLog pdaSeed =
        currentWarning(SUBMISSION_1_ID, CLAIM_1_ID, seedVersion, PDA_SOURCE, "pda-current-warning");
    validationMessageLogRepository.saveAll(List.of(fspSeed, pdaSeed));
    claim.setCaseReferenceNumber("FSP-ONLY-SUPERSESSION");
    entityManager.flush();
    long claimVersion = claim.getVersion();

    persistenceService.persistCurrentWarnings(
        claim, stateWithIssues(warning("WAR003", "fsp-replacement")));
    entityManager.flush();
    entityManager.clear();

    List<ValidationMessageLog> fspWarnings = warningLogsForClaim(CLAIM_1_ID, FSP_SOURCE);
    assertThat(fspWarnings).hasSize(2);
    assertThat(fspWarnings)
        .filteredOn(log -> log.getDisplayMessage().equals("fsp-current-warning"))
        .singleElement()
        .extracting(ValidationMessageLog::getSupersededByVersion)
        .isEqualTo(claimVersion);
    assertThat(fspWarnings)
        .filteredOn(log -> log.getDisplayMessage().equals("fsp-replacement"))
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.getSupersededByVersion())
                  .isEqualTo(ValidationMessageLogRepository.CURRENT_SUPERSEDED_BY_VERSION);
              assertThat(log.getVersion()).isEqualTo(claimVersion);
            });

    List<ValidationMessageLog> pdaWarnings = warningLogsForClaim(CLAIM_1_ID, PDA_SOURCE);
    assertThat(pdaWarnings).hasSize(1);
    assertThat(pdaWarnings.getFirst().getId()).isEqualTo(pdaSeed.getId());
    assertThat(pdaWarnings.getFirst().getSupersededByVersion()).isZero();
    assertThat(pdaWarnings.getFirst().getVersion()).isEqualTo(seedVersion);
  }

  @Test
  @Transactional
  @DisplayName(
      "uses the flushed managed claim version when the claim changed earlier in the transaction")
  void usesFlushedManagedClaimVersionAfterEarlierClaimUpdate() {
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    long seedVersion = claim.getVersion();
    validationMessageLogRepository.save(
        currentFspWarning(SUBMISSION_1_ID, CLAIM_1_ID, seedVersion, "current-warning"));

    claim.setCaseReferenceNumber("UPDATED-IN-TEST");
    entityManager.flush();
    long flushedVersion = claim.getVersion();

    persistenceService.persistCurrentWarnings(
        claim, stateWithIssues(warning("WAR-POST", "post-flush-warning")));
    entityManager.flush();
    entityManager.clear();

    List<ValidationMessageLog> claimWarnings = warningLogsForClaim(CLAIM_1_ID, FSP_SOURCE);
    assertThat(claimWarnings).hasSize(2);
    assertThat(claimWarnings)
        .filteredOn(log -> log.getDisplayMessage().equals("current-warning"))
        .singleElement()
        .extracting(ValidationMessageLog::getSupersededByVersion)
        .isEqualTo(flushedVersion);
    assertThat(claimWarnings)
        .filteredOn(log -> log.getDisplayMessage().equals("post-flush-warning"))
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.getVersion()).isEqualTo(flushedVersion);
              assertThat(log.getSupersededByVersion())
                  .isEqualTo(ValidationMessageLogRepository.CURRENT_SUPERSEDED_BY_VERSION);
            });
  }

  private ClaimAmendmentState stateWithIssues(ValidationIssue... issues) {
    ClaimAmendmentState state =
        ClaimAmendmentState.builder().fspResponseContext(new FeeCalculationResponse()).build();
    state.addWarnings(List.of(issues));
    return state;
  }

  private ValidationIssue warning(String code, String message) {
    return ValidationIssue.builder()
        .code(code)
        .message(message)
        .severity(ValidationSeverity.WARNING)
        .build();
  }

  private ValidationIssue error(String code, String message) {
    return ValidationIssue.builder()
        .code(code)
        .message(message)
        .severity(ValidationSeverity.ERROR)
        .build();
  }

  private List<ValidationMessageLog> warningLogsForClaim(UUID claimId, String source) {
    return validationMessageLogRepository.findAll().stream()
        .filter(log -> claimId.equals(log.getClaimId()))
        .filter(log -> log.getType() == ValidationMessageType.WARNING)
        .filter(log -> source.equals(log.getSource()))
        .toList();
  }
}
