package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ValidationMessageBadRequestException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ValidationMessageMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagesResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ValidationMessageWithClaimDetailsProjection;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.PageableUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ValidationMessageSortField;

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
   * <p>Results may be sorted by any of the following fields: {@code display_message}, {@code
   * client_forename}, {@code client_surname}, {@code unique_client_number}, {@code
   * client_2_forename}, {@code client_2_surname}, {@code client_2_ucn}, {@code unique_file_number}.
   * Sorting is case-insensitive. Where multiple rows share the same primary sort value a
   * deterministic secondary sort by {@code id} in the same direction is applied automatically,
   * preventing row drift across pages.
   *
   * <p><strong>Null / blank handling:</strong> rows whose sort-field value is {@code NULL} are
   * placed <em>last</em> for ascending sorts and <em>first</em> for descending sorts (PostgreSQL
   * default behaviour). This ensures incomplete records always appear at a predictable position.
   *
   * @param submissionId the ID of the submission
   * @param claimId the ID of the claim (optional)
   * @param type the validation message type filter (optional)
   * @param source the source filter (optional)
   * @param pageable pagination details (sort fields are validated and mapped)
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
        repository.findWithClaimDetailsByFilters(
            submissionId,
            claimId,
            type,
            source,
            PageableUtils.validateAndRemap(
                pageable,
                ValidationMessageSortField.values(),
                ValidationMessageBadRequestException::new,
                true));

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
