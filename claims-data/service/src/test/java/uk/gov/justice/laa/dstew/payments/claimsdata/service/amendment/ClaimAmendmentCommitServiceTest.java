package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee.FeeSchemeHandoffFactory;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.ClaimAmendmentPersistenceService;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

@DisplayName("ClaimAmendmentCommitService commit behaviour")
@ExtendWith(MockitoExtension.class)
class ClaimAmendmentCommitServiceTest {

  @Mock private EntityManager entityManager;

  @Mock private ClaimAmendmentPersistenceService persistenceService;

  @Mock private FeeSchemeHandoffFactory handoffFactory;

  @Mock private CalculatedFeeDetailRepository calculatedFeeDetailRepository;

  @InjectMocks private ClaimAmendmentCommitService commitService;

  private Claim detachedClaim;
  private Claim managedClaim;
  private ClaimAmendmentState state;
  private ClaimAmendment amendment;
  private FeeCalculationResponse fspResponse;

  private ListAppender<ILoggingEvent> logAppender;
  private Logger serviceLogger;

  @BeforeEach
  void setUp() {
    // Shared entity fixtures for the successful-commit / fee-detail scenarios.
    detachedClaim = new Claim();
    detachedClaim.setId(UUID.randomUUID());

    managedClaim = new Claim();
    managedClaim.setId(detachedClaim.getId());

    amendment = new ClaimAmendment();
    amendment.setId(UUID.randomUUID());

    fspResponse = new FeeCalculationResponse();
    state = ClaimAmendmentState.builder().fspResponseContext(fspResponse).build();

    // Capture the service logger output for the conflict-logging scenarios.
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
    // Arrange
    UUID claimId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    Claim validatedClaim = mock(Claim.class);
    when(validatedClaim.getId()).thenReturn(claimId);
    when(validatedClaim.getVersion()).thenReturn(7L);
    ClaimAmendmentState mockState = mock(ClaimAmendmentState.class);

    // The versioned UPDATE surfaces at flush, not at merge, so the guard must sit around the flush.
    OptimisticLockException lockException = new OptimisticLockException("stale row");
    when(entityManager.merge(validatedClaim)).thenReturn(validatedClaim);
    doThrow(lockException).when(entityManager).flush();

    // Act & Assert
    assertThatThrownBy(() -> commitService.commit(validatedClaim, mockState))
        .isSameAs(lockException);

    // The write never proceeds to persistence when the guard fails.
    verifyNoInteractions(persistenceService);

    List<ILoggingEvent> warnEvents =
        logAppender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    assertThat(warnEvents).hasSize(1);
    String formatted = warnEvents.getFirst().getFormattedMessage();

    assertThat(formatted)
        .contains("event=CLAIM_VERSION_CONFLICT")
        .contains("claimId=" + claimId)
        .contains("submittedClaimVersion=7")
        .contains("conflictPoint=final_save");
  }

  @Test
  @DisplayName(
      "the final_save conflict WARN never contains claim financial details or amendment payload "
          + "values")
  void shouldNotLogFinancialOrPayloadDetailsAtFinalGuard() {
    // Arrange: the claim carries sensitive values, but the structured guard warning must only emit
    // the safe fields (event, claim id, submitted version, conflict point) - parent AC5.
    String sentinelFeeCode = "SENSITIVE-FEE-9999";
    Claim validatedClaim = mock(Claim.class);
    when(validatedClaim.getId()).thenReturn(UUID.randomUUID());
    when(validatedClaim.getVersion()).thenReturn(7L);
    // The claim carries a fee code, but production must never read it into the log - keep lenient.
    lenient().when(validatedClaim.getFeeCode()).thenReturn(sentinelFeeCode);
    ClaimAmendmentState mockState = mock(ClaimAmendmentState.class);

    // The conflict surfaces at flush (the versioned UPDATE), not at merge.
    OptimisticLockException lockException = new OptimisticLockException("stale row");
    when(entityManager.merge(validatedClaim)).thenReturn(validatedClaim);
    doThrow(lockException).when(entityManager).flush();

    // Act & Assert
    assertThatThrownBy(() -> commitService.commit(validatedClaim, mockState))
        .isSameAs(lockException);

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
    // Arrange
    Claim validatedClaim = mock(Claim.class);
    Claim mergedClaim = mock(Claim.class);
    ClaimAmendmentState mockState = mock(ClaimAmendmentState.class);

    when(entityManager.merge(validatedClaim)).thenReturn(mergedClaim);
    when(persistenceService.persistSuccessfulAmendment(any(), any())).thenReturn(null);

    // Act
    commitService.commit(validatedClaim, mockState);

    // Assert
    assertThat(logAppender.list.stream().anyMatch(e -> e.getLevel() == Level.WARN)).isFalse();
  }

  @Test
  @DisplayName(
      "1595-F: Should atomically commit amendment and save new CalculatedFeeDetail when repricing "
          + "context exists")
  void commit_withFspResponse_savesAmendmentAndFeeDetail() {
    // Arrange
    CalculatedFeeDetail expectedFeeDetail = new CalculatedFeeDetail();
    expectedFeeDetail.setTotalAmount(BigDecimal.valueOf(150.00));

    when(entityManager.merge(detachedClaim)).thenReturn(managedClaim);
    when(persistenceService.persistSuccessfulAmendment(managedClaim, state)).thenReturn(amendment);
    when(handoffFactory.prepareCalculatedFeeDetail(managedClaim, state, fspResponse, amendment))
        .thenReturn(expectedFeeDetail);

    // Act
    ClaimAmendment result = commitService.commit(detachedClaim, state);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(amendment.getId());

    // Verify entity manager merged the claim to trigger optimistic locking
    verify(entityManager).merge(detachedClaim);

    // Verify audit record was saved
    verify(persistenceService).persistSuccessfulAmendment(managedClaim, state);

    // Verify the FSP database record was prepared and saved
    verify(handoffFactory).prepareCalculatedFeeDetail(managedClaim, state, fspResponse, amendment);
    verify(calculatedFeeDetailRepository).save(expectedFeeDetail);
  }

  @Test
  @DisplayName("Should skip saving CalculatedFeeDetail when FSP response context is null")
  void commit_withoutFspResponse_skipsFeeDetailPersistence() {
    // Arrange: Clear the FSP context on the state wrapper
    state.setFspResponseContext(null);

    when(entityManager.merge(detachedClaim)).thenReturn(managedClaim);
    when(persistenceService.persistSuccessfulAmendment(managedClaim, state)).thenReturn(amendment);

    // Act
    ClaimAmendment result = commitService.commit(detachedClaim, state);

    // Assert
    assertThat(result).isNotNull();
    verify(entityManager).merge(detachedClaim);
    verify(persistenceService).persistSuccessfulAmendment(managedClaim, state);

    // Ensure no factory mappings or database saves occurred for fee detail
    verifyNoInteractions(handoffFactory);
    verifyNoInteractions(calculatedFeeDetailRepository);
  }

  @Test
  @DisplayName(
      "Should propagate OptimisticLockException directly when claim has concurrent modifications")
  void commit_onOptimisticLockFailure_propagatesFailureAndAborts() {
    // Arrange: Simulate Hibernate/JPA locking exception on merge
    when(entityManager.merge(detachedClaim))
        .thenThrow(new OptimisticLockException("Stale version"));

    // Act & Assert
    assertThatThrownBy(() -> commitService.commit(detachedClaim, state))
        .isInstanceOf(OptimisticLockException.class)
        .hasMessageContaining("Stale version");

    // Verify execution stopped immediately and no writes were attempted
    verifyNoInteractions(persistenceService);
    verifyNoInteractions(handoffFactory);
    verifyNoInteractions(calculatedFeeDetailRepository);
  }
}
