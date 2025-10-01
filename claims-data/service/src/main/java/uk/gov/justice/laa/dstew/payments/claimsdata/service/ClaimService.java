package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimBadRequestException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimSummaryFeeNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimResultSetMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClientMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionClaim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification.ClaimSpecification;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.lookup.AbstractEntityLookup;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/** Service containing business logic for handling claims. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimService
    implements AbstractEntityLookup<Submission, SubmissionRepository, SubmissionNotFoundException> {
  private final SubmissionRepository submissionRepository;
  private final ClaimRepository claimRepository;
  private final ClientRepository clientRepository;
  private final ClaimMapper claimMapper;
  private final ClientMapper clientMapper;
  private final ValidationMessageLogRepository validationMessageLogRepository;
  private final ClaimResultSetMapper claimResultSetMapper;
  private final ClaimSummaryFeeRepository claimSummaryFeeRepository;
  private final CalculatedFeeDetailRepository calculatedFeeDetailRepository;

  @Override
  public SubmissionRepository lookup() {
    return submissionRepository;
  }

  @Override
  public Supplier<SubmissionNotFoundException> entityNotFoundSupplier(String message) {
    return () -> new SubmissionNotFoundException(message);
  }

  /**
   * Create a claim for a submission.
   *
   * @param submissionId submission identifier
   * @param claimPost request payload
   * @return identifier of the created claim
   */
  @Transactional
  public UUID createClaim(UUID submissionId, ClaimPost claimPost) {
    Submission submission = requireEntity(submissionId);

    Claim claim = claimMapper.toClaim(claimPost);
    claim.setId(Uuid7.timeBasedUuid());
    claim.setSubmission(submission);
    //  TODO: DSTEW-323 replace with the actual user ID/name when available
    claim.setCreatedByUserId("todo");
    claimRepository.save(claim);

    ClaimSummaryFee claimSummaryFee = claimMapper.toClaimSummaryFee(claimPost);
    claimSummaryFee.setId(Uuid7.timeBasedUuid());
    claimSummaryFee.setClaim(claim);
    //  TODO: DSTEW-323 replace with the actual user ID/name when available
    claimSummaryFee.setCreatedByUserId("todo");
    claimSummaryFeeRepository.save(claimSummaryFee);

    Client client = clientMapper.toClient(claimPost);
    if (hasClientData(client)) {
      client.setId(Uuid7.timeBasedUuid());
      client.setClaim(claim);
      //  TODO: DSTEW-323 replace with the actual user ID/name when available
      client.setCreatedByUserId("todo");
      clientRepository.save(client);
    }

    return claim.getId();
  }

  /**
   * Retrieve a claim for a submission.
   *
   * @param submissionId submission identifier
   * @param claimId claim identifier
   * @return populated claim response
   */
  @Transactional(readOnly = true)
  public ClaimResponse getClaim(UUID submissionId, UUID claimId) {
    Claim claim = requireClaim(submissionId, claimId);
    ClaimResponse response = claimMapper.toClaimResponse(claim);
    clientRepository
        .findByClaimId(claimId)
        .ifPresent(client -> clientMapper.updateClaimResponseFromClient(client, response));
    return response;
  }

  /**
   * Update a claim for a submission.
   *
   * @param submissionId submission identifier
   * @param claimId claim identifier
   * @param claimPatch patch payload
   */
  @Transactional
  public void updateClaim(UUID submissionId, UUID claimId, ClaimPatch claimPatch) {
    Claim claim = requireClaim(submissionId, claimId);
    claimMapper.updateSubmissionClaimFromPatch(claimPatch, claim);
    claimRepository.save(claim);

    // If we have calculated fee details from the FSP as part of this patch, save them.
    if (claimPatch.getFeeCalculationResponse() != null) {
      CalculatedFeeDetail calculatedFeeDetail =
          claimMapper.toCalculatedFeeDetail(claimPatch.getFeeCalculationResponse());
      calculatedFeeDetail.setClaimSummaryFee(requireClaimSummaryFee(claim));
      calculatedFeeDetail.setClaim(claim);
      calculatedFeeDetail.setCreatedByUserId("todo");
      calculatedFeeDetailRepository.save(calculatedFeeDetail);
    }

    if (claimPatch.getValidationMessages() != null
        && !claimPatch.getValidationMessages().isEmpty()) {
      claimPatch
          .getValidationMessages()
          .forEach(
              message -> {
                ValidationMessageLog log = claimMapper.toValidationMessageLog(message, claim);
                validationMessageLogRepository.save(log);
              });
    }
  }

  protected ClaimSummaryFee requireClaimSummaryFee(Claim claim) {
    return claimSummaryFeeRepository
        .findByClaim(claim)
        .orElseThrow(
            () ->
                new ClaimSummaryFeeNotFoundException(
                    String.format("No summary fee for claim %s", claim.getId())));
  }

  /**
   * Retrieve claim summaries for a submission.
   *
   * @param submissionId submission identifier
   * @return list of claim summary records
   */
  @Transactional(readOnly = true)
  public List<SubmissionClaim> getClaimsForSubmission(UUID submissionId) {
    return claimRepository.findBySubmissionId(submissionId).stream()
        .map(claimMapper::toSubmissionClaim)
        .toList();
  }

  protected Claim requireClaim(UUID submissionId, UUID claimId) {
    return claimRepository
        .findByIdAndSubmissionId(claimId, submissionId)
        .orElseThrow(
            () ->
                new ClaimNotFoundException(
                    String.format("No claim %s for submission %s", claimId, submissionId)));
  }

  private boolean hasClientData(Client client) {
    return StringUtils.hasText(client.getClientForename())
        || StringUtils.hasText(client.getClientSurname())
        || client.getClientDateOfBirth() != null
        || StringUtils.hasText(client.getClient2Forename())
        || StringUtils.hasText(client.getClient2Surname())
        || client.getClient2DateOfBirth() != null;
  }

  /**
   * Returns all the existing claims filtered by some parameters and paginated in a {@link
   * ClaimResultSet}.
   *
   * @param officeCode a mandatory string representing an office code to filter claims by
   * @param submissionId an optional identifier to filter claims by
   * @param submissionStatuses an optional list of submission statuses to filter claims by
   * @param feeCode an optional string representing a fee code to filter claims by
   * @param uniqueFileNumber the optional unique file number associated to the claim to filter
   *     claims by
   * @param uniqueClientNumber the optional unique client number associated to the claim to filter
   *     claims by
   * @param claimStatuses an optional list of claim statuses to filter claims by
   * @param pageable a pageable object to yield the paginated claims results
   * @return the paginated result set with all claims that satisfy the filtering criteria above.
   */
  public ClaimResultSet getClaimResultSet(
      String officeCode,
      String submissionId,
      List<SubmissionStatus> submissionStatuses,
      String feeCode,
      String uniqueFileNumber,
      String uniqueClientNumber,
      List<ClaimStatus> claimStatuses,
      Pageable pageable) {

    if (!StringUtils.hasText(officeCode)) {
      throw new ClaimBadRequestException("Missing office code");
    }

    Page<Claim> page =
        claimRepository.findAll(
            ClaimSpecification.filterBy(
                officeCode,
                submissionId,
                submissionStatuses,
                feeCode,
                uniqueFileNumber,
                uniqueClientNumber,
                claimStatuses),
            pageable);

    return claimResultSetMapper.toClaimResultSet(page);
  }
}
