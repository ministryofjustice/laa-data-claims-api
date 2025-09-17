package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ValidationMessageMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagesResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;

/** Service containing business logic for handling validation errors. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationMessageService {

  private final ValidationMessageLogRepository repository;
  private final ValidationMessageMapper mapper;

  /**
   * Retrieves validation errors by submission and claim IDs with pagination.
   *
   * @param submissionId the ID of the submission
   * @param claimId the ID of the claim
   * @param pageable pagination details
   * @return a response containing paginated validation errors
   */
  public ValidationMessagesResponse getValidationErrors(
      UUID submissionId,
      UUID claimId,
      ValidationMessageType type,
      String source,
      Pageable pageable) {
    log.info("Fetching validation errors for submissionId={}, claimId={}", submissionId, claimId);

    ValidationMessageLog example = new ValidationMessageLog();
    example.setSubmissionId(submissionId);
    example.setClaimId(claimId);
    example.setType(type);
    example.setSource(source);

    Page<ValidationMessageLog> page = repository.findAll(Example.of(example), pageable);

    ValidationMessagesResponse response = mapper.toValidationMessagesResponse(page);
    response.setTotalClaims(getTotalUniqueClaimsWithErrors(submissionId, claimId, type));

    return response;
  }

  private int getTotalUniqueClaimsWithErrors(
      UUID submissionId, UUID claimId, ValidationMessageType type) {
    if (claimId != null) {
      return 1;
    }
    return (int) repository.countDistinctClaimIdsBySubmissionIdAndType(submissionId, type);
  }
}
