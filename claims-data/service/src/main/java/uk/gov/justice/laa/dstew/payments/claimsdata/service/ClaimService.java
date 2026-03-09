package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.ClaimSearchRequest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimBadRequestException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimSummaryFeeNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.factory.AssessmentFactory;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimResultSetMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClientMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponseV2;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSetV2;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionClaim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AssessmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimCaseRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimWarningCountProjection;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification.ClaimSpecification;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.lookup.AbstractEntityLookup;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimSortField;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/** Service containing business logic for handling claims. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimService
    implements AbstractEntityLookup<Submission, SubmissionRepository, SubmissionNotFoundException> {
  public static final String INVALID_CLAIM_STATUS_UPDATE_MESSAGE =
      "Claim status VOID cannot be set via %s endpoint. Use POST /api/v1/claims/{claimId}/void";

  private final SubmissionRepository submissionRepository;
  private final ClaimRepository claimRepository;
  private final ClientRepository clientRepository;
  private final ClaimMapper claimMapper;
  private final ClientMapper clientMapper;
  private final ValidationMessageLogRepository validationMessageLogRepository;
  private final ClaimResultSetMapper claimResultSetMapper;
  private final ClaimSummaryFeeRepository claimSummaryFeeRepository;
  private final CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  private final ClaimCaseRepository claimCaseRepository;
  private final AssessmentRepository assessmentRepository;
  private final ClaimValidationService claimValidationService;
  private final AssessmentFactory assessmentFactory;

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
    claim.setCreatedByUserId(claimPost.getCreatedByUserId());
    claimRepository.save(claim);

    ClaimSummaryFee claimSummaryFee = claimMapper.toClaimSummaryFee(claimPost);
    claimSummaryFee.setId(Uuid7.timeBasedUuid());
    claimSummaryFee.setClaim(claim);
    claimSummaryFee.setCreatedByUserId(claimPost.getCreatedByUserId());
    claimSummaryFeeRepository.save(claimSummaryFee);

    ClaimCase claimCase = claimMapper.toClaimCase(claimPost);
    claimCase.setId(Uuid7.timeBasedUuid());
    claimCase.setClaim(claim);
    claimCase.setCreatedByUserId(claimPost.getCreatedByUserId());
    claimCaseRepository.save(claimCase);

    Client client = clientMapper.toClient(claimPost);
    if (hasClientData(client)) {
      client.setId(Uuid7.timeBasedUuid());
      client.setClaim(claim);
      client.setCreatedByUserId(claimPost.getCreatedByUserId());
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
    claimSummaryFeeRepository
        .findByClaimId(claimId)
        .ifPresent(fee -> claimMapper.updateClaimResponseFromClaimSummaryFee(fee, response));
    calculatedFeeDetailRepository
        .findByClaimId(claimId)
        .ifPresent(
            feeDetail ->
                claimMapper.updateClaimResponseFromCalculatedFeeDetail(feeDetail, response));
    claimCaseRepository
        .findByClaimId(claimId)
        .ifPresent(claimCase -> claimMapper.updateClaimResponseFromClaimCase(claimCase, response));
    return response;
  }

  /**
   * Retrieve a claim for a submission.
   *
   * @param submissionId submission identifier
   * @param claimId claim identifier
   * @return populated claim response v2
   */
  @Transactional(readOnly = true)
  public ClaimResponseV2 getClaimV2(UUID submissionId, UUID claimId) {
    Claim claim = requireClaim(submissionId, claimId);
    return claimMapper.toClaimResponseV2(claim);
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

    if (claimPatch.getStatus() == ClaimStatus.VOID) {
      throw new ClaimBadRequestException(
          INVALID_CLAIM_STATUS_UPDATE_MESSAGE.formatted("update claim"));
    }
    claimMapper.updateSubmissionClaimFromPatch(claimPatch, claim);
    claimRepository.save(claim);

    // If we have calculated fee details from the FSP as part of this patch, save them.
    if (claimPatch.getFeeCalculationResponse() != null) {
      CalculatedFeeDetail calculatedFeeDetail =
          claimMapper.toCalculatedFeeDetail(claimPatch.getFeeCalculationResponse());
      // Set created on date, ID is set within ClaimMapper so Hibernate will never set this for you.
      calculatedFeeDetail.setCreatedOn(OffsetDateTime.now());

      // Get existing calculated fee detail, and set the ID if it exists
      calculatedFeeDetailRepository
          .findByClaimId(claimId)
          .ifPresent(x -> calculatedFeeDetail.setId(x.getId()));

      calculatedFeeDetail.setClaimSummaryFee(requireClaimSummaryFee(claim));
      calculatedFeeDetail.setClaim(claim);
      calculatedFeeDetail.setCreatedByUserId(claimPatch.getCreatedByUserId());
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
      String uniqueCaseId,
      List<ClaimStatus> claimStatuses,
      String submissionPeriod,
      String caseReferenceNumber,
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
                uniqueCaseId,
                claimStatuses,
                submissionPeriod,
                caseReferenceNumber),
            pageable);

    ClaimResultSet response = claimResultSetMapper.toClaimResultSet(page);
    for (ClaimResponse claimResponse : response.getContent()) {
      if (claimResponse.getId() != null) {
        clientRepository
            .findByClaimId(UUID.fromString(claimResponse.getId()))
            .ifPresent(client -> clientMapper.updateClaimResponseFromClient(client, claimResponse));
        claimSummaryFeeRepository
            .findByClaimId(UUID.fromString(claimResponse.getId()))
            .ifPresent(
                fee -> claimMapper.updateClaimResponseFromClaimSummaryFee(fee, claimResponse));
        calculatedFeeDetailRepository
            .findByClaimId(UUID.fromString(claimResponse.getId()))
            .ifPresent(
                feeDetail ->
                    claimMapper.updateClaimResponseFromCalculatedFeeDetail(
                        feeDetail, claimResponse));
        claimCaseRepository
            .findByClaimId(UUID.fromString(claimResponse.getId()))
            .ifPresent(
                claimCase ->
                    claimMapper.updateClaimResponseFromClaimCase(claimCase, claimResponse));
        long totalWarningsForClaim =
            validationMessageLogRepository.countAllByClaimIdAndType(
                UUID.fromString(claimResponse.getId()), ValidationMessageType.WARNING);
        claimMapper.updateTotalWarningMessages(totalWarningsForClaim, claimResponse);
      }
    }
    return response;
  }

  /**
   * Returns all the existing claims filtered by some parameters and paginated in a {@link
   * ClaimResultSet}.
   *
   * @param request an object containing all the parameters to filter by
   * @param pageable a pageable object to yield the paginated claims results
   * @return the paginated result set with all claims that satisfy the filtering criteria above.
   */
  public ClaimResultSetV2 getClaimResultSetV2(ClaimSearchRequest request, Pageable pageable) {

    if (!StringUtils.hasText(request.getOfficeCode())) {
      throw new ClaimBadRequestException("Missing office code");
    }

    Pageable mappedPageable = mapPageableSort(pageable);

    Specification<Claim> baseSpec = ClaimSpecification.filterBy(request);
    Specification<Claim> warningSortSpec =
        ClaimSpecification.orderByTotalWarningMessages(mappedPageable);
    Specification<Claim> submissionPeriodSortSpec =
        ClaimSpecification.orderBySubmissionPeriod(mappedPageable);
    Specification<Claim> combinedSpec = baseSpec.and(warningSortSpec).and(submissionPeriodSortSpec);

    Pageable sanitizedPageable = removeCustomSortFromPageable(mappedPageable, "totalWarnings");
    sanitizedPageable =
        removeCustomSortFromPageable(sanitizedPageable, "submission.submissionPeriod");

    Page<Claim> page = claimRepository.findAll(combinedSpec, sanitizedPageable);

    ClaimResultSetV2 response = claimResultSetMapper.toClaimResultSetV2(page);

    List<UUID> claimIds =
        response.getContent().stream()
            .map(ClaimResponseV2::getId)
            .filter(Objects::nonNull)
            .map(UUID::fromString)
            .distinct()
            .toList();

    if (!claimIds.isEmpty()) {
      // 2) Fetch all warning counts in a single query
      Map<UUID, Long> warningsByClaimId =
          validationMessageLogRepository
              .countWarningsByClaimIdsAndType(claimIds, ValidationMessageType.WARNING)
              .stream()
              .collect(
                  Collectors.toMap(
                      ClaimWarningCountProjection::getClaimId,
                      ClaimWarningCountProjection::getWarningCount));

      // 3) Apply counts to each ClaimResponse (pure in-memory)
      for (ClaimResponseV2 claimResponse : response.getContent()) {
        if (claimResponse.getId() != null) {
          UUID claimId = UUID.fromString(claimResponse.getId());
          long totalWarningsForClaim = warningsByClaimId.getOrDefault(claimId, 0L);

          claimMapper.updateTotalWarningMessagesV2(totalWarningsForClaim, claimResponse);
        }
      }
    }

    return response;
  }

  private Pageable mapPageableSort(Pageable pageable) {
    Sort originalSort = pageable.getSort();

    if (originalSort.isUnsorted()) {
      return pageable;
    }

    Sort mappedSort = Sort.by(originalSort.stream().map(this::mapOrder).toList());

    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), mappedSort);
  }

  private Sort.Order mapOrder(Sort.Order order) {
    String apiProperty = order.getProperty();

    ClaimSortField sortField =
        ClaimSortField.fromApiName(apiProperty)
            .orElseThrow(
                () -> new ClaimBadRequestException("Unsupported sort field: " + apiProperty));

    return new Sort.Order(order.getDirection(), sortField.getEntityPath());
  }

  @Transactional
  public int updateAllClaimsStatusForSubmission(UUID submissionId, ClaimStatus status) {
    return claimRepository.updateStatusBySubmissionId(submissionId, status);
  }

  /**
   * Voids a claim by its identifier and creates an associated assessment. This operation validates
   * the claim's eligibility for voiding based on input parameters.
   *
   * @param claimId the unique identifier of the claim to be voided
   * @param createdByUserId the identifier of the user initiating the void operation
   * @param assessmentReason the reason for the assessment creation during claim voiding
   * @return the unique identifier of the newly created assessment
   */
  @Transactional
  public UUID voidClaimByIdAndCreateAssessment(
      UUID claimId, UUID createdByUserId, String assessmentReason) {

    claimValidationService.validateVoidClaimParameters(claimId, createdByUserId, assessmentReason);

    Claim claim = claimValidationService.getValidClaimOrThrow(claimId);
    ClaimSummaryFee claimSummaryFee =
        claimValidationService.getClaimSummaryFeeByClaimIdOrThrow(claimId);

    claim.voidClaim(createdByUserId);
    Assessment assessment =
        assessmentFactory.createVoidAssessment(
            assessmentReason, claim, claimSummaryFee, createdByUserId);
    return assessmentRepository.save(assessment).getId();
  }

  private Pageable removeCustomSortFromPageable(Pageable pageable, String customProperty) {
    if (pageable == null || pageable.getSort().isUnsorted()) {
      return pageable;
    }

    List<Sort.Order> remainingOrders =
        pageable.getSort().stream()
            .filter(order -> !customProperty.equalsIgnoreCase(order.getProperty()))
            .toList();

    Sort newSort = remainingOrders.isEmpty() ? Sort.unsorted() : Sort.by(remainingOrders);

    return org.springframework.data.domain.PageRequest.of(
        pageable.getPageNumber(), pageable.getPageSize(), newSort);
  }
}
