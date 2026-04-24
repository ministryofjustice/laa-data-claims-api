package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ValidationMessageMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagesResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ValidationMessageWithClaimDetailsProjection;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@ExtendWith(MockitoExtension.class)
class ValidationMessageServiceTest {

  @Mock private ValidationMessageLogRepository repository;
  @Mock private ValidationMessageMapper mapper;

  @InjectMocks private ValidationMessageService service;

  @Test
  @DisplayName(
      "should return validation errors enriched with claim details when claimId is present")
  void shouldReturnValidationErrorsForClaim() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();
    final Pageable pageable = PageRequest.of(0, 10);
    final var page =
        new PageImpl<>(List.of(mock(ValidationMessageWithClaimDetailsProjection.class)));
    final ValidationMessagesResponse mappedResponse = new ValidationMessagesResponse();

    when(repository.findWithClaimDetailsByFilters(
            submissionId, claimId, ValidationMessageType.ERROR, "SYSTEM", pageable))
        .thenReturn(page);
    when(mapper.toValidationMessagesResponseFromProjection(page)).thenReturn(mappedResponse);

    ValidationMessagesResponse result =
        service.getValidationErrors(
            submissionId, claimId, ValidationMessageType.ERROR, "SYSTEM", pageable);

    assertThat(result).isSameAs(mappedResponse);
    // claimId is non-null so totalClaims should be 1
    assertThat(result.getTotalClaims()).isEqualTo(1);
    verify(repository)
        .findWithClaimDetailsByFilters(
            submissionId, claimId, ValidationMessageType.ERROR, "SYSTEM", pageable);
    verify(mapper).toValidationMessagesResponseFromProjection(page);
    verifyNoMoreInteractions(repository, mapper);
  }

  @Test
  @DisplayName(
      "should return validation errors enriched with claim details and count distinct claims when"
          + " claimId is null")
  void shouldReturnValidationErrorsAndCountDistinctClaims() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final Pageable pageable = PageRequest.of(0, 5);
    final var page =
        new PageImpl<>(List.of(mock(ValidationMessageWithClaimDetailsProjection.class)));
    final ValidationMessagesResponse mappedResponse = new ValidationMessagesResponse();

    when(repository.findWithClaimDetailsByFilters(
            submissionId, null, ValidationMessageType.WARNING, "USER", pageable))
        .thenReturn(page);
    when(mapper.toValidationMessagesResponseFromProjection(page)).thenReturn(mappedResponse);
    when(repository.countDistinctClaimIdsBySubmissionIdAndType(
            submissionId, ValidationMessageType.WARNING))
        .thenReturn(3L);

    ValidationMessagesResponse result =
        service.getValidationErrors(
            submissionId, null, ValidationMessageType.WARNING, "USER", pageable);

    assertThat(result).isSameAs(mappedResponse);
    assertThat(result.getTotalClaims()).isEqualTo(3);
    verify(repository)
        .findWithClaimDetailsByFilters(
            submissionId, null, ValidationMessageType.WARNING, "USER", pageable);
    verify(mapper).toValidationMessagesResponseFromProjection(page);
    verify(repository)
        .countDistinctClaimIdsBySubmissionIdAndType(submissionId, ValidationMessageType.WARNING);
    verifyNoMoreInteractions(repository, mapper);
  }

  @Test
  @DisplayName("should pass null type and source through to the repository")
  void shouldPassNullFiltersToRepository() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final Pageable pageable = PageRequest.of(0, 20);
    final var page = new PageImpl<ValidationMessageWithClaimDetailsProjection>(List.of());
    final ValidationMessagesResponse mappedResponse = new ValidationMessagesResponse();

    when(repository.findWithClaimDetailsByFilters(submissionId, null, null, null, pageable))
        .thenReturn(page);
    when(mapper.toValidationMessagesResponseFromProjection(page)).thenReturn(mappedResponse);
    when(repository.countDistinctClaimIdsBySubmissionIdAndType(submissionId, null)).thenReturn(0L);

    ValidationMessagesResponse result =
        service.getValidationErrors(submissionId, null, null, null, pageable);

    assertThat(result).isSameAs(mappedResponse);
    assertThat(result.getTotalClaims()).isZero();
    verify(repository).findWithClaimDetailsByFilters(submissionId, null, null, null, pageable);
  }
}
