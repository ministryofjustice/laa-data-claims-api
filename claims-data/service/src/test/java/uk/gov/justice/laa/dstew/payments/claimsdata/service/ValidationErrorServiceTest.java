package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationErrorLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ValidationErrorMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationErrorsResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationErrorLogRepository;

@ExtendWith(MockitoExtension.class)
class ValidationErrorServiceTest {

  @Mock private ValidationErrorLogRepository repository;

  @Mock private ValidationErrorMapper mapper;

  @InjectMocks private ValidationErrorService service;

  @Test
  @DisplayName("should return mapped validation errors when claimId is provided")
  void shouldReturnValidationErrorsWhenClaimIdProvided() {
    UUID submissionId = UUID.randomUUID();
    UUID claimId = UUID.randomUUID();
    Pageable pageable = PageRequest.of(0, 10);

    ValidationErrorLog entity = new ValidationErrorLog();
    entity.setSubmissionId(submissionId);
    entity.setClaimId(claimId);

    Page<ValidationErrorLog> page = new PageImpl<>(List.of(entity));
    ValidationErrorsResponse mappedResponse = new ValidationErrorsResponse().totalElements(1);

    when(repository.findAll(any(Example.class), eq(pageable))).thenReturn(page);
    when(mapper.toValidationErrorsResponse(page)).thenReturn(mappedResponse);

    ValidationErrorsResponse result = service.getValidationErrors(submissionId, claimId, pageable);

    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getTotalClaims()).isEqualTo(1);

    verify(repository).findAll(any(Example.class), eq(pageable));
    verify(mapper).toValidationErrorsResponse(page);
  }

  @Test
  @DisplayName(
      "should return mapped validation errors and distinct claim count when claimId is null")
  void shouldReturnValidationErrorsAndDistinctClaimCountWhenClaimIdIsNull() {
    UUID submissionId = UUID.randomUUID();
    Pageable pageable = PageRequest.of(0, 10);

    ValidationErrorLog entity = new ValidationErrorLog();
    entity.setSubmissionId(submissionId);
    entity.setClaimId(UUID.randomUUID());

    Page<ValidationErrorLog> page = new PageImpl<>(List.of(entity));
    ValidationErrorsResponse mappedResponse = new ValidationErrorsResponse().totalElements(1);

    when(repository.findAll(any(Example.class), eq(pageable))).thenReturn(page);
    when(mapper.toValidationErrorsResponse(page)).thenReturn(mappedResponse);
    when(repository.countDistinctClaimIdsBySubmissionId(submissionId)).thenReturn(5L);

    ValidationErrorsResponse result = service.getValidationErrors(submissionId, null, pageable);

    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getTotalClaims()).isEqualTo(5);

    verify(repository).findAll(any(Example.class), eq(pageable));
    verify(mapper).toValidationErrorsResponse(page);
    verify(repository).countDistinctClaimIdsBySubmissionId(submissionId);
  }
}
