package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.ClaimSearchRequest;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimAmendmentValidationException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimBadRequestException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimSummaryFeeNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimResultSetMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClientMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimAmendmentPatch;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ClaimAmendmentService;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ClaimAmendmentStateService;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.lookup.AbstractEntityLookup;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimSortField;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.DataNormaliser;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;
import uk.gov.justice.laa.dstew.payments.claimsdata.validator.ClaimSearchRequestValidator;

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
  private final ClaimCaseRepository claimCaseRepository;
  private final AssessmentRepository assessmentRepository;
  private final ClaimValidationService claimValidationService;
  private final AssessmentService assessmentService;
  private final ClaimSearchRequestValidator claimSearchRequestValidator;
  private final ClaimAmendmentService claimAmendmentService;
  private final ClaimAmendmentStateService claimAmendmentStateService;

  private static final Set<String> IGNORED_FIELDS =
      Set.of(
          "id",
          "submissionId",
          "status",
          "validationMessages",
          "feeCalculationResponse",
          "version",
          "createdByUserId");

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
        .findFirstByClaimIdOrderByCreatedOnDescIdDesc(claimId)
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
  public void updateClaim(UUID submissionId, UUID claimId, ClaimAmendmentPatch claimPatch) {
    Claim claim = requireClaim(submissionId, claimId);

    if (isAnAmendment(claimPatch, claim)) {
      amendClaim(claim, claimPatch);
    } else {
      updateClaimStatusAndFeeDetails(claim, claimPatch);
    }
  }

  private boolean isAnAmendment(ClaimAmendmentPatch claimPatch, Claim claim) {
    if (claimPatch.getStatus() == null) {
      return true;
    }
    return hasAdditionalFieldUpdates(claimPatch, claim);
  }

  /**
   * Checks if the patch contains any fields outside of the standard status/fee update flow.
   * Leverages short-circuit evaluation for maximum performance.
   *
   * <p>Amendment fields are wrapped in {@link JsonNullable}: an omitted field ({@code
   * JsonNullable.undefined()}) is skipped. A present, <strong>non-null</strong> value that differs
   * from the persisted claim counts as an update (and therefore an amendment).
   *
   * <p>An explicit null (a "clear") is deliberately <strong>not</strong> treated as an amendment
   * trigger here. Clearing a persisted field back to {@code null} is an amendment-only feature; on
   * the legacy status/fee path an explicit null is a no-op, mirroring the pre-amendment contract
   * where an absent and an explicitly-null field were indistinguishable.
   */
  private boolean hasAdditionalFieldUpdates(ClaimAmendmentPatch patch, Claim claim) {
    if (patch == null) {
      return false;
    }

    AtomicBoolean hasUpdates = new AtomicBoolean(false);

    ReflectionUtils.doWithFields(
        patch.getClass(),
        patchField -> {
          if (hasUpdates.get() || IGNORED_FIELDS.contains(patchField.getName())) {
            return;
          }
          if (fieldRepresentsChange(patchField, patch, claim)) {
            hasUpdates.set(true);
          }
        },
        ReflectionUtils.COPYABLE_FIELDS);

    return hasUpdates.get();
  }

  /**
   * Determines whether a single patch field carries a real, persistable change relative to the
   * claim. Omitted fields and explicit-null "clears" never count - only a present, non-null value
   * that differs from the persisted claim does.
   */
  private boolean fieldRepresentsChange(Field patchField, ClaimAmendmentPatch patch, Claim claim) {
    ReflectionUtils.makeAccessible(patchField);
    Object rawValue = ReflectionUtils.getField(patchField, patch);

    // Tri-state fields: omitted or explicit null -> skip; present non-null -> consider.
    Object candidateValue = rawValue;
    if (rawValue instanceof JsonNullable<?> jsonNullable) {
      candidateValue = jsonNullable.isPresent() ? jsonNullable.get() : null;
    }

    return candidateValue != null
        && !Objects.equals(candidateValue, readClaimField(claim, patchField.getName()));
  }

  /**
   * Neutralises explicit-null "clears" on the legacy status/fee update path.
   *
   * <p>Setting a persisted field back to {@code null} is an amendment-only feature. On the legacy
   * path an explicit JSON null must behave exactly like an omitted field - a no-op - matching the
   * pre-amendment contract. Converting any present-null {@link JsonNullable} back to {@link
   * JsonNullable#undefined()} makes the MapStruct mapper skip it instead of writing {@code null}
   * over the existing value.
   */
  private void stripExplicitNullClears(ClaimAmendmentPatch patch) {
    if (patch == null) {
      return;
    }

    ReflectionUtils.doWithFields(
        patch.getClass(),
        patchField -> {
          ReflectionUtils.makeAccessible(patchField);
          if (ReflectionUtils.getField(patchField, patch) instanceof JsonNullable<?> jsonNullable
              && jsonNullable.isPresent()
              && jsonNullable.get() == null) {
            ReflectionUtils.setField(patchField, patch, JsonNullable.undefined());
          }
        },
        ReflectionUtils.COPYABLE_FIELDS);
  }

  private Object readClaimField(Claim claim, String fieldName) {
    Field claimField = ReflectionUtils.findField(claim.getClass(), fieldName);
    if (claimField == null) {
      return null;
    }
    ReflectionUtils.makeAccessible(claimField);
    return ReflectionUtils.getField(claimField, claim);
  }

  /**
   * This method is called to allow legacy updates to still work.
   *
   * @param claim claim
   * @param claimPatch claim patch
   */
  private void updateClaimStatusAndFeeDetails(Claim claim, ClaimAmendmentPatch claimPatch) {

    // Field clearing is amendment-only: on the legacy path an explicit null must not overwrite an
    // existing value, so strip explicit-null "clears" before mapping onto the entity.
    stripExplicitNullClears(claimPatch);

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

    claimValidationService.ensureStatusIsNotVoid(claimPatch.getStatus());
    claimMapper.updateSubmissionClaimFromPatch(claimPatch, claim);
    claimRepository.save(claim);

    // If we have calculated fee details from the FSP as part of this patch, save them.
    if (claimPatch.getFeeCalculationResponse() != null) {
      CalculatedFeeDetail calculatedFeeDetail =
          claimMapper.toCalculatedFeeDetail(claimPatch.getFeeCalculationResponse());
      // Set created on date, ID is set within ClaimMapper so Hibernate will never set this for you.
      calculatedFeeDetail.setCreatedOn(Instant.now());

      // Get existing calculated fee detail, and set the ID if it exists
      calculatedFeeDetailRepository
          .findFirstByClaimIdOrderByCreatedOnDescIdDesc(claim.getId())
          .ifPresent(x -> calculatedFeeDetail.setId(x.getId()));

      calculatedFeeDetail.setClaimSummaryFee(requireClaimSummaryFee(claim));
      calculatedFeeDetail.setClaim(claim);
      calculatedFeeDetail.setCreatedByUserId(claimPatch.getCreatedByUserId().orElse(null));
      calculatedFeeDetailRepository.save(calculatedFeeDetail);
    }
  }

  private void amendClaim(Claim claim, ClaimAmendmentPatch claimPatch) {

    if (claimPatch.getValidationMessages() != null
        && !claimPatch.getValidationMessages().isEmpty()) {
      claimPatch
          .getValidationMessages()
          .forEach(
              message -> {
                ValidationMessageLog validationLog =
                    claimMapper.toValidationMessageLog(message, claim);
                validationMessageLogRepository.save(validationLog);
              });
    }

    ClaimAmendmentPayload payload = claimMapper.toAmendmentPayload(claimPatch);

    ClaimAmendmentResult result = claimAmendmentService.submitAmendment(claim, payload);

    if (result.errors() != null && !result.errors().isEmpty()) {
      throw new ClaimAmendmentValidationException(result.errors());
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
   * Returns all existing claims filtered by the supplied parameters and paginated in a {@link
   * ClaimResultSet}.
   *
   * <p><strong>Deprecated</strong>: this v1 API is deprecated as of Apr 1st 2026. Use {@link
   * #getClaimResultSetV2(ClaimSearchRequest, Pageable)} instead. The v2 method accepts a {@link
   * ClaimSearchRequest}, centralises normalisation and validation, and provides the improved CRN
   * matching and sorting behaviour expected by clients.
   *
   * <p>Migration notes:
   *
   * <ul>
   *   <li>V2 requires an instance of {@link ClaimSearchRequest} rather than a positional parameter
   *       list. Prefer constructing that DTO and calling {@link
   *       DataNormaliser#normaliseClaimSearchRequest(ClaimSearchRequest)} before validation if you
   *       still need the same normalisation behaviour.
   *   <li>Office code remains mandatory in both versions.
   *   <li>V2 supports the broader, case-insensitive CRN matching and mapped sorting (for example,
   *       by totalWarnings and submissionPeriod) and should be used for new clients.
   * </ul>
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
   * @deprecated Use {@link #getClaimResultSetV2(ClaimSearchRequest, Pageable)}. Deprecated as of
   *     Apr 1st 2026.
   */
  @Deprecated(since = "Apr 1st 2026")
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

    claimSearchRequestValidator.validateOfficeCode(officeCode);

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
            .findFirstByClaimIdOrderByCreatedOnDescIdDesc(UUID.fromString(claimResponse.getId()))
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

    // Normalise before validation.
    DataNormaliser.normaliseClaimSearchRequest(request);
    claimSearchRequestValidator.validate(request);

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
        assessmentService.createVoidAssessment(
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
