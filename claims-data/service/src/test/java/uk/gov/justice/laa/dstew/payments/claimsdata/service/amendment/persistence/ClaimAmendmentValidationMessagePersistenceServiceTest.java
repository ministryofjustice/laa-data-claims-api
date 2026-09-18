package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationIssue;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationSeverity;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ValidationMessageLogFactory;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

@ExtendWith(MockitoExtension.class)
class ClaimAmendmentValidationMessagePersistenceServiceTest {

  @Mock private ValidationMessageLogRepository validationMessageLogRepository;
  @Mock private ValidationMessageLogFactory validationMessageLogFactory;

  @InjectMocks private ClaimAmendmentValidationMessagePersistenceService persistenceService;

  @Captor private ArgumentCaptor<List<ValidationMessageLog>> savedWarningsCaptor;

  @Test
  void persistCurrentWarnings_supersedesExistingCurrentWarningsAndPersistsOnlyWarnings() {
    Claim claim =
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .version(12L)
            .submission(Submission.builder().id(Uuid7.timeBasedUuid()).build())
            .build();
    ClaimAmendmentState state =
        ClaimAmendmentState.builder().fspResponseContext(new FeeCalculationResponse()).build();
    ValidationIssue warning =
        ValidationIssue.builder()
            .code("WAR001")
            .message("warning")
            .severity(ValidationSeverity.WARNING)
            .build();
    ValidationIssue error =
        ValidationIssue.builder()
            .code("ERR001")
            .message("error")
            .severity(ValidationSeverity.ERROR)
            .build();
    state.addWarnings(List.of(warning, error));

    ValidationMessageLog warningLog = new ValidationMessageLog();
    when(validationMessageLogFactory.createForClaimIssue(
            warning,
            claim,
            ValidationMessageType.WARNING,
            ClaimAmendmentValidationMessagePersistenceService.FSP_SOURCE,
            12L,
            ValidationMessageLogRepository.CURRENT_SUPERSEDED_BY_VERSION))
        .thenReturn(warningLog);

    persistenceService.persistCurrentWarnings(claim, state);

    verify(validationMessageLogRepository)
        .supersedeCurrentByClaimIdAndSource(
            claim.getId(),
            ClaimAmendmentValidationMessagePersistenceService.FSP_SOURCE,
            ValidationMessageType.WARNING,
            12L,
            ValidationMessageLogRepository.CURRENT_SUPERSEDED_BY_VERSION);
    verify(validationMessageLogFactory)
        .createForClaimIssue(
            warning,
            claim,
            ValidationMessageType.WARNING,
            ClaimAmendmentValidationMessagePersistenceService.FSP_SOURCE,
            12L,
            ValidationMessageLogRepository.CURRENT_SUPERSEDED_BY_VERSION);
    verify(validationMessageLogFactory, never())
        .createForClaimIssue(
            eq(error),
            eq(claim),
            eq(ValidationMessageType.WARNING),
            eq(ClaimAmendmentValidationMessagePersistenceService.FSP_SOURCE),
            eq(12L),
            eq(ValidationMessageLogRepository.CURRENT_SUPERSEDED_BY_VERSION));
    verify(validationMessageLogRepository).saveAll(savedWarningsCaptor.capture());
    assertThat(savedWarningsCaptor.getValue()).containsExactly(warningLog);
  }

  @Test
  void persistCurrentWarnings_returnsEarlyWhenNoFspResponseContextIsPresent() {
    Claim claim =
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .version(12L)
            .submission(Submission.builder().id(Uuid7.timeBasedUuid()).build())
            .build();
    ClaimAmendmentState state = ClaimAmendmentState.builder().build();

    persistenceService.persistCurrentWarnings(claim, state);

    verifyNoInteractions(validationMessageLogRepository, validationMessageLogFactory);
  }
}
