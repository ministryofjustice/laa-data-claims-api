package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.ClaimAmendmentPersistenceService;

@DisplayName("ClaimAmendmentCommitService final-guard conflict logging")
class ClaimAmendmentCommitServiceTest {

  private EntityManager entityManager;
  private ClaimAmendmentPersistenceService persistenceService;
  private ClaimAmendmentCommitService commitService;

  private ListAppender<ILoggingEvent> logAppender;
  private Logger serviceLogger;

  @BeforeEach
  void setUp() {
    entityManager = mock(EntityManager.class);
    persistenceService = mock(ClaimAmendmentPersistenceService.class);
    commitService = new ClaimAmendmentCommitService(entityManager, persistenceService);

    serviceLogger = (Logger) LoggerFactory.getLogger(ClaimAmendmentCommitService.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    serviceLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    serviceLogger.detachAppender(logAppender);
  }

  @Test
  @DisplayName(
      "a stale-version merge failure logs a WARN with safe fields at the final_save point and "
          + "rethrows the exception")
  void logsStructuredWarnAndRethrowsOnOptimisticLockFailure() {
    UUID claimId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    Claim validatedClaim = mock(Claim.class);
    when(validatedClaim.getId()).thenReturn(claimId);
    when(validatedClaim.getVersion()).thenReturn(7L);
    ClaimAmendmentState state = mock(ClaimAmendmentState.class);

    OptimisticLockException lockException = new OptimisticLockException("stale row");
    when(entityManager.merge(validatedClaim)).thenThrow(lockException);

    assertThatThrownBy(() -> commitService.commit(validatedClaim, state)).isSameAs(lockException);

    // The write never proceeds to persistence when the guard fails.
    verifyNoInteractions(persistenceService);

    List<ILoggingEvent> warnEvents =
        logAppender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    assertThat(warnEvents).hasSize(1);
    String formatted = warnEvents.getFirst().getFormattedMessage();

    assertThat(formatted).contains("event=CLAIM_VERSION_CONFLICT");
    assertThat(formatted).contains("claimId=" + claimId);
    assertThat(formatted).contains("submittedClaimVersion=7");
    assertThat(formatted).contains("conflictPoint=final_save");
  }

  @Test
  @DisplayName(
      "the final_save conflict WARN never contains claim financial details or amendment payload "
          + "values")
  void shouldNotLogFinancialOrPayloadDetailsAtFinalGuard() {
    // The claim carries sensitive values, but the structured guard warning must only emit the safe
    // fields (event, claim id, submitted version, conflict point) - parent AC5.
    String sentinelFeeCode = "SENSITIVE-FEE-9999";
    Claim validatedClaim = mock(Claim.class);
    when(validatedClaim.getId()).thenReturn(UUID.randomUUID());
    when(validatedClaim.getVersion()).thenReturn(7L);
    when(validatedClaim.getFeeCode()).thenReturn(sentinelFeeCode);
    ClaimAmendmentState state = mock(ClaimAmendmentState.class);

    OptimisticLockException lockException = new OptimisticLockException("stale row");
    when(entityManager.merge(validatedClaim)).thenThrow(lockException);

    assertThatThrownBy(() -> commitService.commit(validatedClaim, state)).isSameAs(lockException);

    String formatted =
        logAppender.list.stream()
            .filter(e -> e.getLevel() == Level.WARN)
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + b);

    assertThat(formatted).doesNotContain(sentinelFeeCode);
  }

  @Test
  @DisplayName("a successful commit persists the amendment and logs no conflict warning")
  void successfulCommitLogsNoWarning() {
    Claim validatedClaim = mock(Claim.class);
    Claim managedClaim = mock(Claim.class);
    ClaimAmendmentState state = mock(ClaimAmendmentState.class);

    when(entityManager.merge(validatedClaim)).thenReturn(managedClaim);
    when(persistenceService.persistSuccessfulAmendment(any(), any())).thenReturn(null);

    commitService.commit(validatedClaim, state);

    assertThat(logAppender.list.stream().anyMatch(e -> e.getLevel() == Level.WARN)).isFalse();
  }
}
