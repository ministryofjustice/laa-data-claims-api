package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationErrorLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ValidationErrorMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetValidationErrors200Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationErrorLogRepository;

/** Service containing business logic for handling validation errors. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationErrorService {

  private final ValidationErrorLogRepository repository;
  private final ValidationErrorMapper mapper;

  public GetValidationErrors200Response getValidationErrors(
      UUID submissionId, UUID claimId, Pageable pageable) {
    log.info("Fetching validation errors for submissionId={}, claimId={}", submissionId, claimId);

    ValidationErrorLog example = new ValidationErrorLog();
    example.setSubmissionId(submissionId);
    example.setClaimId(claimId);

    Page<ValidationErrorLog> page = repository.findAll(Example.of(example), pageable);

    return mapper.toGetValidationErrors200Response(page);
  }
}
