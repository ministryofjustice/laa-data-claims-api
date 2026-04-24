package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ValidationMessageMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagesResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ValidationMessageWithClaimDetailsProjection;

/** Service containing business logic for handling validation errors. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationMessageService {

  private final ValidationMessageLogRepository repository;
  private final ValidationMessageMapper mapper;

  /**
   * Retrieves validation errors by submission and claim IDs with pagination. Each result item is
   * enriched with client/claim details (client names, UCNs, UFN) read at query time from the claim
   * and client tables.
   *
   * @param submissionId the ID of the submission
   * @param claimId the ID of the claim (optional)
   * @param type the validation message type filter (optional)
   * @param source the source filter (optional)
   * @param pageable pagination details
   * @return a response containing paginated validation errors with claim details
   */
  public ValidationMessagesResponse getValidationErrors(
      UUID submissionId,
      UUID claimId,
      ValidationMessageType type,
      String source,
      Pageable pageable) {
    log.info("Fetching validation errors for submissionId={}, claimId={}", submissionId, claimId);

    Page<ValidationMessageWithClaimDetailsProjection> page =
        repository.findWithClaimDetailsByFilters(submissionId, claimId, type, source, pageable);

    ValidationMessagesResponse response = mapper.toValidationMessagesResponseFromProjection(page);
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
