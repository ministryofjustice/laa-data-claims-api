package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee.FeeSchemeHandoffFactory;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.ClaimAmendmentPersistenceService;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

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

  @BeforeEach
  void setUp() {
    detachedClaim = new Claim();
    detachedClaim.setId(UUID.randomUUID());

    managedClaim = new Claim();
    managedClaim.setId(detachedClaim.getId());

    amendment = new ClaimAmendment();
    amendment.setId(UUID.randomUUID());

    fspResponse = new FeeCalculationResponse();

    state = ClaimAmendmentState.builder().fspResponseContext(fspResponse).build();
  }

  @Test
  @DisplayName(
      "1595-F: Should atomically commit amendment and save new CalculatedFeeDetail when repricing context exists")
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
