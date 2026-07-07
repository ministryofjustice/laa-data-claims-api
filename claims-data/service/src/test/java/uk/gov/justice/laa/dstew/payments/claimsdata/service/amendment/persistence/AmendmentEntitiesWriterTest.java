package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimCaseRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/** Tests for {@link AmendmentEntitiesWriter}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("AmendmentEntitiesWriter Tests")
class AmendmentEntitiesWriterTest {

  @Mock private AmendmentClaimUpdater claimUpdater;
  @Mock private AmendmentClientUpdater clientUpdater;
  @Mock private AmendmentClaimCaseUpdater claimCaseUpdater;
  @Mock private AmendmentClaimSummaryFeeUpdater claimSummaryFeeUpdater;
  @Mock private ClientRepository clientRepository;
  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private ClaimSummaryFeeRepository claimSummaryFeeRepository;

  @InjectMocks private AmendmentEntitiesWriter entitiesWriter;

  private static final UUID CLAIM_ID = Uuid7.timeBasedUuid();
  private final ClaimStateSnapshot post = ClaimStateSnapshot.builder().claimId(CLAIM_ID).build();

  @Test
  @DisplayName("applies claim and all present related entities and marks the claim amended")
  void appliesClaimAndRelatedEntities() {
    Claim claim = Claim.builder().id(CLAIM_ID).build();
    Client client = Client.builder().id(Uuid7.timeBasedUuid()).build();
    ClaimCase claimCase = ClaimCase.builder().id(Uuid7.timeBasedUuid()).build();
    ClaimSummaryFee summaryFee = ClaimSummaryFee.builder().id(Uuid7.timeBasedUuid()).build();

    when(clientRepository.findByClaimId(CLAIM_ID)).thenReturn(Optional.of(client));
    when(claimCaseRepository.findByClaimId(CLAIM_ID)).thenReturn(Optional.of(claimCase));
    when(claimSummaryFeeRepository.findByClaimId(CLAIM_ID)).thenReturn(Optional.of(summaryFee));

    entitiesWriter.applyAmendedValues(claim, post);

    verify(claimUpdater).applyAmendedFields(claim, post);
    assertThat(claim.isAmended()).isTrue();
    verify(clientUpdater).applyAmendedFields(client, post);
    verify(claimCaseUpdater).applyAmendedFields(claimCase, post);
    verify(claimSummaryFeeUpdater).applyAmendedFields(summaryFee, post);
  }

  @Test
  @DisplayName("skips related entities that do not exist for the claim")
  void skipsAbsentRelatedEntities() {
    Claim claim = Claim.builder().id(CLAIM_ID).build();

    when(clientRepository.findByClaimId(CLAIM_ID)).thenReturn(Optional.empty());
    when(claimCaseRepository.findByClaimId(CLAIM_ID)).thenReturn(Optional.empty());
    when(claimSummaryFeeRepository.findByClaimId(CLAIM_ID)).thenReturn(Optional.empty());

    entitiesWriter.applyAmendedValues(claim, post);

    verify(claimUpdater).applyAmendedFields(claim, post);
    assertThat(claim.isAmended()).isTrue();
    verifyNoInteractions(clientUpdater, claimCaseUpdater, claimSummaryFeeUpdater);
  }
}
