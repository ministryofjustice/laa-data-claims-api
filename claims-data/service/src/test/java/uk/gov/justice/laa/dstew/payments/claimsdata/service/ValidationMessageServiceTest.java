package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ValidationMessageMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagesResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;

@ExtendWith(MockitoExtension.class)
class ValidationMessageServiceTest {

  @Mock private ValidationMessageLogRepository repository;
  @Mock private ValidationMessageMapper mapper;

  @InjectMocks private ValidationMessageService service;

  @Test
  @DisplayName("should return validation errors for given submissionId and claimId")
  void shouldReturnValidationErrorsForClaim() {
    final UUID submissionId = UUID.randomUUID();
    final UUID claimId = UUID.randomUUID();
    final Pageable pageable = PageRequest.of(0, 10);
    final ValidationMessageLog logEntity = new ValidationMessageLog();
    final Page<ValidationMessageLog> page = new PageImpl<>(List.of(logEntity));
    final ValidationMessagesResponse mappedResponse = new ValidationMessagesResponse();

    when(repository.findAll(any(Example.class), eq(pageable))).thenReturn(page);
    when(mapper.toValidationMessagesResponse(page)).thenReturn(mappedResponse);

    ValidationMessagesResponse result =
        service.getValidationErrors(submissionId, claimId, "ERROR", "SYSTEM", pageable);

    assertThat(result).isSameAs(mappedResponse);
    assertThat(result.getTotalClaims()).isEqualTo(1);
    verify(repository).findAll(any(Example.class), eq(pageable));
    verify(mapper).toValidationMessagesResponse(page);
    verifyNoMoreInteractions(repository, mapper);
  }

  @Test
  @DisplayName("should return validation errors and count distinct claims when claimId is null")
  void shouldReturnValidationErrorsAndCountDistinctClaims() {
    final UUID submissionId = UUID.randomUUID();
    final Pageable pageable = PageRequest.of(0, 5);
    final ValidationMessageLog logEntity = new ValidationMessageLog();
    final Page<ValidationMessageLog> page = new PageImpl<>(List.of(logEntity));
    final ValidationMessagesResponse mappedResponse = new ValidationMessagesResponse();

    when(repository.findAll(any(Example.class), eq(pageable))).thenReturn(page);
    when(mapper.toValidationMessagesResponse(page)).thenReturn(mappedResponse);
    when(repository.countDistinctClaimIdsBySubmissionId(submissionId)).thenReturn(3L);

    ValidationMessagesResponse result =
        service.getValidationErrors(submissionId, null, "WARNING", "USER", pageable);

    assertThat(result).isSameAs(mappedResponse);
    assertThat(result.getTotalClaims()).isEqualTo(3);
    verify(repository).findAll(any(Example.class), eq(pageable));
    verify(mapper).toValidationMessagesResponse(page);
    verify(repository).countDistinctClaimIdsBySubmissionId(submissionId);
    verifyNoMoreInteractions(repository, mapper);
  }
}
