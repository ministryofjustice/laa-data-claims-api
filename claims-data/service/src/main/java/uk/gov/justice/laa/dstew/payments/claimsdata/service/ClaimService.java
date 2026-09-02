package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification.ClaimSpecification.CALCULATED_FEE_DETAILS;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.DuplicateClaimException;
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
          "createdByUserId",
          // Read-only field computed from the vw_claim_effective_value view; must not count as a
          // provider-requested change that triggers the amendment path.
          "effectiveTotalValue");

  private static final Set<String> COMPUTED_SORT_PATHS =
      Set.of(
          "totalWarnings",
          "submission.submissionPeriod",
          "derivedClaimStatus",
          CALCULATED_FEE_DETAILS + ".calculatedVatAmount",
          CALCULATED_FEE_DETAILS + ".totalAmount",
          CALCULATED_FEE_DETAILS + ".escapeCaseFlag",
          CALCULATED_FEE_DETAILS + ".categoryOfLaw");

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

    // Belt-and-braces duplicate guard. The authoritative, race-safe enforcement is the database
    // partial unique index (uq_claim_submission_line_number); this pre-check simply gives callers a
    // clean 409 (DuplicateClaimException) on the common path and fails fast before any writes.
    //
    // CAVEAT (old-vs-new): the DB index is PARTIAL - it grandfathers pre-existing historical
    // duplicates (business rule: we never amend or delete historical data), so it cannot catch a
    // NEW claim that duplicates an OLD (grandfathered) row. This pre-check queries all rows, so it
    // DOES cover that old-vs-new case. It is currently unreachable (claims are only added to
    // newly-created submissions, never appended to historical ones) but is guarded here defensively
    // in case that business rule ever changes.
    //
    // RACE: this check is not atomic with the insert below, so a small TOCTOU window remains if two
    // requests create the same (submission_id, line_number) concurrently. The DB unique index
    // closes that window for the common (post-cutoff) case; the residual race only affects the
    // old-vs-new scenario above and is considered minimal/acceptable.
    Integer lineNumber = claimPost.getLineNumber();
    if (lineNumber != null
        && claimRepository.existsBySubmissionIdAndLineNumber(submissionId, lineNumber)) {
      throw new DuplicateClaimException(
          String.format(
              "A claim with line number %d already exists for the submission.", lineNumber));
    }

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

    if (candidateValue == null) {
      return false;
    }

    // Resolve the entity field name for mapped booleans (isX -> x) or other simple mappings.
    String patchFieldName = patchField.getName();
    Field claimField = ReflectionUtils.findField(claim.getClass(), patchFieldName);
    if (claimField == null) {
      String mappedName = mapPatchFieldNameToEntityField(patchFieldName);
      if (!mappedName.equals(patchFieldName)) {
        claimField = ReflectionUtils.findField(claim.getClass(), mappedName);
      }
    }

    if (claimField == null) {
      // No matching entity field found: preserve previous behaviour (present non-null -> amendment)
      return !Objects.equals(candidateValue, null);
    }

    ReflectionUtils.makeAccessible(claimField);
    Object persistedValue = ReflectionUtils.getField(claimField, claim);

    Object normalisedCandidate = convertCandidateToFieldType(candidateValue, claimField.getType());

    return !Objects.equals(normalisedCandidate, persistedValue);
  }

  private String mapPatchFieldNameToEntityField(String patchFieldName) {
    // Simple MapStruct-style boolean mapping: isDutySolicitor -> dutySolicitor
    if (patchFieldName != null && patchFieldName.length() > 2 && patchFieldName.startsWith("is")
        && Character.isUpperCase(patchFieldName.charAt(2))) {
      String withoutIs = patchFieldName.substring(2);
      return Character.toLowerCase(withoutIs.charAt(0)) + withoutIs.substring(1);
    }
    return patchFieldName;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object convertCandidateToFieldType(Object candidateValue, Class<?> targetType) {
    if (candidateValue == null || targetType == null) {
      return candidateValue;
    }

    // Already correct type
    if (targetType.isInstance(candidateValue)) {
      return candidateValue;
    }

    try {
      // String -> LocalDate
      if (candidateValue instanceof String s) {
        if (LocalDate.class.equals(targetType)) {
          try {
            return LocalDate.parse(s, ClaimMapper.CLAIM_DATE_FORMAT);
          } catch (Exception ex1) {
            try {
              // Fallback: accept ISO yyyy-MM-dd commonly emitted by LocalDate.toString()
              return LocalDate.parse(s);
            } catch (Exception ex2) {
              // fall through to leave as raw string (will be considered a change)
              return candidateValue;
            }
          }
        }
        if (BigDecimal.class.equals(targetType)) {
          try {
            return new BigDecimal(s);
          } catch (Exception e) {
            return candidateValue;
          }
        }
        if (Boolean.class.equals(targetType) || boolean.class.equals(targetType)) {
          return Boolean.valueOf(s);
        }
        if (Integer.class.equals(targetType) || int.class.equals(targetType)) {
          try {
            return Integer.valueOf(s);
          } catch (Exception e) {
            return candidateValue;
          }
        }
        if (Long.class.equals(targetType) || long.class.equals(targetType)) {
          try {
            return Long.valueOf(s);
          } catch (Exception e) {
            return candidateValue;
          }
        }
        if (targetType.isEnum()) {
          Object[] constants = targetType.getEnumConstants();
          for (Object c : constants) {
            if (c.toString().equalsIgnoreCase(s) || ((Enum) c).name().equalsIgnoreCase(s)) {
              return c;
            }
          }
          return candidateValue;
        }
      }

      // Number -> BigDecimal
      if (candidateValue instanceof Number n && BigDecimal.class.equals(targetType)) {
        return BigDecimal.valueOf(n.doubleValue());
      }

      // LocalDate -> String (format)
      if (candidateValue instanceof LocalDate ld && String.class.equals(targetType)) {
        return ld.format(ClaimMapper.CLAIM_DATE_FORMAT);
      }
    } catch (Exception e) {
      // Conversion failed - fall back to original candidateValue
      return candidateValue;
    }

    // Fallback: return original candidateValue
    return candidateValue;
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

    Pageable sanitizedPageable = removeComputedSorts(mappedPageable);

    // Deterministic ordering:
    //  - A computed sort (totalWarnings, submissionPeriod, derivedClaimStatus, latest calculated
    //    fee) applies its own id tie-break inside the ordering Specification. When every requested
    //    sort is computed the sanitized Pageable is left unsorted, so Spring Data does not override
    //    that Specification ordering and we must not append a tie-break here.
    //  - Any plain-column sort that survives sanitisation is applied by Spring Data and would
    //    override the Specification ordering, so it needs the id tie-break appended here. This also
    //    covers requests that mix a plain-column sort with a computed sort (e.g.
    //    sort=effective_total_value,asc&sort=total_warnings,asc): stripping the computed order must
    //    not leave the surviving plain sort without a deterministic tie-break.
    //  - The unsorted default likewise gets the id tie-break appended.
    if (sanitizedPageable.getSort().isSorted() || !hasComputedSort(mappedPageable)) {
      sanitizedPageable = appendIdTieBreak(sanitizedPageable);
    }

    Specification<Claim> feeSortSpec =
        ClaimSpecification.orderByLatestCalculatedFee(mappedPageable);
    Specification<Claim> combinedSpec =
        ClaimSpecification.filterBy(request)
            .and(ClaimSpecification.orderByTotalWarningMessages(mappedPageable))
            .and(ClaimSpecification.orderBySubmissionPeriod(mappedPageable))
            .and(ClaimSpecification.orderByDerivedClaimStatus(mappedPageable))
            .and(feeSortSpec);

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

    List<Sort.Order> mappedOrders = originalSort.stream().map(this::mapOrder).toList();
    Sort mappedSort = Sort.by(mappedOrders);

    // An unpaged request still carries a sort, but exposes no page number/size; building a
    // PageRequest from it would throw. Return an unpaged pageable that keeps the mapped sort so all
    // rows come back ordered, mirroring the unsorted-unpaged path handled above.
    if (pageable.isUnpaged()) {
      return Pageable.unpaged(mappedSort);
    }

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

  private boolean hasComputedSort(Pageable pageable) {
    if (pageable == null || pageable.getSort().isUnsorted()) {
      return false;
    }
    return pageable.getSort().stream()
        .anyMatch(order -> COMPUTED_SORT_PATHS.contains(order.getProperty()));
  }

  private Pageable removeComputedSorts(Pageable pageable) {
    if (pageable == null || pageable.getSort().isUnsorted()) {
      return pageable;
    }

    List<Sort.Order> remainingOrders =
        pageable.getSort().stream()
            .filter(order -> !COMPUTED_SORT_PATHS.contains(order.getProperty()))
            .toList();

    Sort newSort = remainingOrders.isEmpty() ? Sort.unsorted() : Sort.by(remainingOrders);

    if (pageable.isUnpaged()) {
      return newSort.isSorted() ? Pageable.unpaged(newSort) : Pageable.unpaged();
    }

    return org.springframework.data.domain.PageRequest.of(
        pageable.getPageNumber(), pageable.getPageSize(), newSort);
  }

  /**
   * Appends a deterministic secondary sort by {@code id} (ascending, UUIDv7) so rows never drift
   * between pages. No-op for unpaged requests.
   */
  private Pageable appendIdTieBreak(Pageable pageable) {
    if (pageable == null) {
      return null;
    }
    Sort sortWithTieBreak = pageable.getSort().and(Sort.by(Sort.Direction.ASC, "id"));
    return pageable.isUnpaged()
        ? Pageable.unpaged(sortWithTieBreak)
        : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortWithTieBreak);
  }
}
