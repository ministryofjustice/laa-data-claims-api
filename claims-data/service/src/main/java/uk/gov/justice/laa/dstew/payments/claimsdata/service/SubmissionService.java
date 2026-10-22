package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationResult;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.service.ValidationService;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.DuplicateSubmissionException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionBadRequestException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionOfficeAuthorisationException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionValidationException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionsResultSetMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionClaim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionsResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification.SubmissionSpecification;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.coercion.StatusCoercer;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.lookup.AbstractEntityLookup;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.BigDecimalUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.PageableUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.SubmissionSortField;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.TransactionalPublisher;
import uk.gov.justice.laa.dstew.payments.claimsevent.model.SubmissionEventType;

/** Service containing business logic for handling submissions. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionService
    implements AbstractEntityLookup<Submission, SubmissionRepository, SubmissionNotFoundException> {
  public static final short DECIMAL_PLACES = 2;

  /**
   * Statuses that mark a submission as superseded (no longer "live"). A submission in one of these
   * states does not participate in the office/area-of-law/period uniqueness rule, so a failed or
   * replaced submission never blocks a fresh attempt. Mirrors the {@code WHERE status NOT IN (...)}
   * predicate of the partial DB index {@code uq_submission_live_office_aol_period}.
   */
  private static final List<SubmissionStatus> NON_LIVE_STATUSES =
      List.of(SubmissionStatus.VALIDATION_FAILED, SubmissionStatus.REPLACED);

  private final ValidationService validationService;
  private final SubmissionRepository submissionRepository;
  private final SubmissionMapper submissionMapper;
  private final ClaimService claimService;
  private final MatterStartService matterStartService;
  private final ValidationMessageLogRepository validationMessageLogRepository;
  private final ValidationMessageLogFactory validationMessageLogFactory;
  private final SubmissionsResultSetMapper submissionsResultSetMapper;
  private final SubmissionEventPublisherService submissionEventPublisherService;
  private final StatusCoercer statusCoercer;
  private final ClaimsApiProperties claimsApiProperties;
  private final AssessmentService assessmentService;

  @Override
  public SubmissionRepository lookup() {
    return submissionRepository;
  }

  @Override
  public Supplier<SubmissionNotFoundException> entityNotFoundSupplier(String message) {
    return () -> new SubmissionNotFoundException(message);
  }

  /**
   * Create and persist a new submission.
   *
   * @param submissionPost request body
   * @return id of the created submission
   */
  public UUID createSubmission(SubmissionPost submissionPost) {
    Submission submission = submissionMapper.toSubmission(submissionPost);
    submission.setCreatedByUserId(submissionPost.getCreatedByUserId());
    submission.setUpdatedByUserId(submissionPost.getCreatedByUserId());

    // This is to ensure that we are only validating form NIL submissions where SubmissionStatus is
    // always READY_FOR_VALIDATION. All other submissions will skip this validation, as they get
    // created by the event service with CREATED status
    if (submission.getStatus() != SubmissionStatus.CREATED) {
      ValidationResult validationResult =
          validationService.validateSubmission(submissionMapper.toSubmissionResponse(submission));
      if (!validationResult.isValid()) {
        throw new SubmissionValidationException(
            "Submission failed validation", validationResult.getIssues());
      }
      submission.setStatus(SubmissionStatus.VALIDATED_PENDING_APPROVAL);
      statusCoercer.coerce(submission);
      if (submission.getCreatedOn() == null) {
        submission.setCreatedOn(Instant.now());
      }
    }

    requireNoConflictingLiveSubmission(submission);

    submissionRepository.save(submission);

    if (submission.getStatus() == SubmissionStatus.VALIDATED_PENDING_APPROVAL) {
      publishValidationSucceededAfterCommit(
          submission.getId(), SubmissionEventType.INITIAL_SUBMISSION_VALIDATION_SUCCEEDED);
    } else if (submission.getStatus() == SubmissionStatus.VALIDATION_SUCCEEDED) {
      publishValidationSucceededAfterCommit(
          submission.getId(), SubmissionEventType.SUBMISSION_VALIDATION_SUCCEEDED);
    }

    return submission.getId();
  }

  /**
   * Fail-fast guard that rejects a submission duplicating an existing live one for the same office,
   * area of law and period, giving callers a clean {@link DuplicateSubmissionException} (409)
   * before any write. The authoritative, race-safe enforcement is the database partial unique index
   * {@code uq_submission_live_office_aol_period}; this pre-check simply covers the common path.
   *
   * <p>The check is skipped when any keying field is absent, since the DB index only constrains
   * rows with a complete key and there is nothing to conflict on.
   *
   * @param submission the submission about to be persisted
   */
  private void requireNoConflictingLiveSubmission(Submission submission) {
    if (submission.getOfficeAccountNumber() == null
        || submission.getAreaOfLaw() == null
        || submission.getSubmissionPeriod() == null) {
      return;
    }

    if (hasConflictingLiveSubmission(
        submission.getOfficeAccountNumber(),
        submission.getAreaOfLaw(),
        submission.getSubmissionPeriod())) {
      throw new DuplicateSubmissionException(
          "A live submission already exists for office %s, area of law %s and period %s"
              .formatted(
                  submission.getOfficeAccountNumber(),
                  submission.getAreaOfLaw(),
                  submission.getSubmissionPeriod()));
    }
  }

  /**
   * Checks if a live submission exists for the given office, area of law and period.
   *
   * @param officeAccountNumber the office account number
   * @param areaOfLaw the area of law
   * @param submissionPeriod the submission period
   * @return true if a live submission exists, false otherwise
   */
  public boolean hasConflictingLiveSubmission(
      String officeAccountNumber, AreaOfLaw areaOfLaw, String submissionPeriod) {
    return submissionRepository
        .existsByOfficeAccountNumberAndAreaOfLawAndSubmissionPeriodAndStatusNotIn(
            officeAccountNumber, areaOfLaw, submissionPeriod, NON_LIVE_STATUSES);
  }

  /**
   * Retrieve a submission by its identifier.
   *
   * @param id the submission id
   * @return submission response model
   */
  @Transactional(readOnly = true)
  public SubmissionResponse getSubmission(UUID id) {
    Submission submission = requireEntity(id);

    List<SubmissionClaim> claims = claimService.getClaimsForSubmission(id);

    List<UUID> matterStartIds = matterStartService.getMatterStartIdsForSubmission(id);

    var calculatedTotalAmount = submissionRepository.getCalculatedTotalAmount(id);
    var assessedTotalAmount = assessmentService.getAssessedTotalAmount(id);

    return new SubmissionResponse()
        .submissionId(submission.getId())
        .bulkSubmissionId(submission.getBulkSubmissionId())
        .officeAccountNumber(submission.getOfficeAccountNumber())
        .submissionPeriod(submission.getSubmissionPeriod())
        .areaOfLaw(submission.getAreaOfLaw())
        .status(submission.getStatus())
        .crimeLowerScheduleNumber(submission.getCrimeLowerScheduleNumber())
        .legalHelpSubmissionReference(submission.getLegalHelpSubmissionReference())
        .mediationSubmissionReference(submission.getMediationSubmissionReference())
        .previousSubmissionId(submission.getPreviousSubmissionId())
        .isNilSubmission(submission.getIsNilSubmission())
        .numberOfClaims(submission.getNumberOfClaims())
        .submitted(toOffsetDateTime(submission.getCreatedOn()))
        .approvedOn(toOffsetDateTime(submission.getApprovedOn()))
        .approvedByUserId(submission.getApprovedByUserId())
        .claims(claims)
        .calculatedTotalAmount(BigDecimalUtils.scaleNullable(calculatedTotalAmount, DECIMAL_PLACES))
        .assessedTotalAmount(BigDecimalUtils.scaleNullable(assessedTotalAmount, DECIMAL_PLACES))
        .matterStarts(matterStartIds)
        .createdByUserId(submission.getCreatedByUserId())
        .providerUserId(submission.getProviderUserId())
        .errorMessages(submission.getErrorMessages());
  }

  /**
   * Converts a nullable {@link Instant} to an {@link OffsetDateTime} in the system time zone.
   *
   * @param instant the instant to convert, may be null
   * @return the converted value, or null when the instant is null
   */
  private static OffsetDateTime toOffsetDateTime(Instant instant) {
    return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
  }

  /**
   * Partially update a submission.
   *
   * @param id the submission id
   * @param submissionPatch patch object containing updated fields
   */
  @Transactional
  public void updateSubmission(UUID id, SubmissionPatch submissionPatch) {
    if (submissionPatch.getStatus() == SubmissionStatus.READY_FOR_FINAL_VALIDATION) {
      updateFinalValidationSubmission(id, submissionPatch);
      return;
    }

    Submission submission =
        submissionRepository
            .findByIdAndLockForUpdate(id)
            .orElseThrow(() -> entityNotFoundSupplier("No entity found with id: " + id).get());

    if (submissionPatch.getStatus() == SubmissionStatus.FINAL_VALIDATION_IN_PROGRESS) {
      requireAwaitingFinalValidation(submission);
    }

    if (submissionPatch.getCreatedByUserId() == null) {
      log.warn(
          "Tombstone: Submission patch received without created_by_user_id. "
              + "submissionId={}, officeAccountNumber={}, providerUserId={}, areaOfLaw={}",
          id,
          submission.getOfficeAccountNumber(),
          submission.getProviderUserId(),
          submission.getAreaOfLaw());
    }

    statusCoercer.coerce(submissionPatch);
    submissionMapper.updateSubmissionFromPatch(submissionPatch, submission);
    submissionRepository.save(submission);

    if (submissionPatch.getStatus() == SubmissionStatus.READY_FOR_VALIDATION) {
      TransactionalPublisher.runAfterCommit(
          () ->
              submissionEventPublisherService.publishSubmissionValidationEvent(submission.getId()));
    } else if (submissionPatch.getStatus() == SubmissionStatus.VALIDATED_PENDING_APPROVAL) {
      publishValidationSucceededAfterCommit(
          submission.getId(), SubmissionEventType.INITIAL_SUBMISSION_VALIDATION_SUCCEEDED);
    } else if (submissionPatch.getStatus() == SubmissionStatus.VALIDATION_SUCCEEDED) {
      publishValidationSucceededAfterCommit(
          submission.getId(), SubmissionEventType.SUBMISSION_VALIDATION_SUCCEEDED);
    } else if (submissionPatch.getStatus() == SubmissionStatus.VALIDATION_FAILED) {
      int totalUpdatedClaims =
          claimService.updateAllClaimsStatusForSubmission(
              id, ClaimStatus.INVALID, submissionPatch.getCreatedByUserId());
      log.debug("Updated {} claims to INVALID status for submission {}", totalUpdatedClaims, id);
    }

    if (submissionPatch.getValidationMessages() != null
        && !submissionPatch.getValidationMessages().isEmpty()) {
      submissionPatch
          .getValidationMessages()
          .forEach(
              message -> {
                ValidationMessageLog validationLog =
                    validationMessageLogFactory.createForSubmission(message, submission);
                validationMessageLogRepository.save(validationLog);
              });
    }
  }

  private void updateFinalValidationSubmission(UUID id, SubmissionPatch submissionPatch) {
    requireValidatedPendingApprovalLifecycle();

    Submission submission =
        submissionRepository
            .findByIdAndLockForUpdate(id)
            .orElseThrow(() -> entityNotFoundSupplier("No entity found with id: " + id).get());

    requireMatchingOffice(submissionPatch, submission);
    final String approvingUserId = requireApprovingUser(submissionPatch);

    if (submission.getStatus() == SubmissionStatus.READY_FOR_FINAL_VALIDATION) {
      return;
    }

    if (submission.getStatus() != SubmissionStatus.VALIDATED_PENDING_APPROVAL) {
      throw new SubmissionBadRequestException(
          "Submission is not eligible for approval in status: " + submission.getStatus());
    }

    // Queuing final validation is a state transition, not a partial update: only the status and
    // the approving user are written, so an approval request cannot alter submission content.
    // The approval is recorded separately from updatedOn/updatedByUserId, which the event service
    // overwrites as final validation progresses.
    submission.setStatus(SubmissionStatus.READY_FOR_FINAL_VALIDATION);
    submission.setUpdatedByUserId(approvingUserId);
    submission.setApprovedByUserId(approvingUserId);
    submission.setApprovedOn(Instant.now());
    submissionRepository.save(submission);
    TransactionalPublisher.runAfterCommit(
        () -> submissionEventPublisherService.publishSubmissionApprovalEvent(id));
  }

  /**
   * Refuses to mark final validation as in progress unless the submission is waiting for it. The
   * event service sets this status when it picks up the approval event, and SQS can redeliver that
   * event, so a submission that has already reached an outcome must not be pulled back into the
   * lifecycle. Restating the status is permitted so that a redelivered event is a harmless no-op
   * rather than a failure that ends up in the dead letter queue.
   */
  private void requireAwaitingFinalValidation(Submission submission) {
    SubmissionStatus currentStatus = submission.getStatus();
    if (currentStatus != SubmissionStatus.READY_FOR_FINAL_VALIDATION
        && currentStatus != SubmissionStatus.FINAL_VALIDATION_IN_PROGRESS) {
      throw new SubmissionBadRequestException(
          "Submission is not awaiting final validation in status: " + currentStatus);
    }
  }

  /**
   * Refuses the transition unless the validated-pending-approval lifecycle is enabled. {@code
   * READY_FOR_FINAL_VALIDATION} belongs to that lifecycle, and nothing in this service moves a
   * submission out of it: the outcome is owned by the event service, which only reruns final
   * validation once the lifecycle is live. Queuing a submission while the lifecycle is disabled
   * would therefore leave it in a status it can never leave.
   */
  private void requireValidatedPendingApprovalLifecycle() {
    if (!claimsApiProperties.getFeatures().isValidatedPendingApprovalEnabled()) {
      throw new SubmissionBadRequestException(
          "Final validation is not enabled: the validated-pending-approval lifecycle is disabled");
    }
  }

  /**
   * Confirms the caller is acting for the office that owns the submission. Authorisation itself is
   * owned by the calling service; this guards against a caller approving a submission belonging to
   * a different provider. The office account number is mandatory for this transition, so the check
   * cannot be skipped by omitting it.
   */
  private void requireMatchingOffice(SubmissionPatch submissionPatch, Submission submission) {
    String requestedOffice = submissionPatch.getOfficeAccountNumber();

    if (requestedOffice == null || requestedOffice.isBlank()) {
      throw new SubmissionBadRequestException(
          "Office account number is required to start final validation");
    }

    if (!requestedOffice.equals(submission.getOfficeAccountNumber())) {
      // The office held against the submission is deliberately not echoed back to the caller.
      throw new SubmissionOfficeAuthorisationException(
          "You do not have access to the office account for this submission");
    }
  }

  /**
   * Returns the user approving the submission, carried on the patch as {@code createdByUserId} (the
   * user making the request). Approval is a deliberate user action, so it is always attributable
   * and the transition is refused when the acting user is absent.
   */
  private String requireApprovingUser(SubmissionPatch submissionPatch) {
    String approvingUserId = submissionPatch.getCreatedByUserId();

    if (approvingUserId == null || approvingUserId.isBlank()) {
      throw new SubmissionBadRequestException("User id is required to start final validation");
    }

    return approvingUserId;
  }

  /**
   * Returns all the existing submissions filtered by some parameters and paginated in a {@link
   * SubmissionsResultSet}.
   *
   * @param offices a mandatory list of office codes to filter submissions by
   * @param submissionId an optional identifier to filter submissions by
   * @param submittedDateFrom an optional end date to filter submissions created on or after this
   *     date
   * @param submittedDateTo an optional end date to filter submissions created on or before this
   *     date
   * @param submissionStatuses an optional list of submission statuses to filter submissions by
   * @param pageable a pageable object to yield the paginated submission results
   * @return the paginated result set with all submissions that satisfy the filtering criteria
   *     above.
   */
  @Transactional(readOnly = true)
  public SubmissionsResultSet getSubmissionsResultSet(
      List<String> offices,
      String submissionId,
      LocalDate submittedDateFrom,
      LocalDate submittedDateTo,
      AreaOfLaw areaOfLaw,
      String submissionPeriod,
      List<SubmissionStatus> submissionStatuses,
      Pageable pageable) {

    if (offices == null || offices.isEmpty()) {
      throw new SubmissionBadRequestException("Missing offices list");
    }

    Pageable stablePageable =
        PageableUtils.validateAndRemap(
            pageable, SubmissionSortField.values(), SubmissionBadRequestException::new, true);

    Page<Submission> page =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(offices)
                .and(SubmissionSpecification.submissionIdEqualTo(submissionId))
                .and(SubmissionSpecification.createdOnOrAfter(submittedDateFrom))
                .and(SubmissionSpecification.createdOnOrBefore(submittedDateTo))
                .and(SubmissionSpecification.areaOfLawEqual(areaOfLaw))
                .and(SubmissionSpecification.submissionPeriodEqual(submissionPeriod))
                .and(SubmissionSpecification.submissionStatusIn(submissionStatuses)),
            stablePageable);

    SubmissionsResultSet resultSet = submissionsResultSetMapper.toSubmissionsResultSet(page);
    List<UUID> submissionIds = page.getContent().stream().map(Submission::getId).toList();

    if (submissionIds.isEmpty()) {
      return resultSet;
    }

    Map<UUID, BigDecimal> assessedTotalAmounts =
        assessmentService.getAssessedTotalAmounts(submissionIds);
    Map<UUID, BigDecimal> calculatedTotalAmounts = getCalculatedTotalAmounts(submissionIds);
    resultSet
        .getContent()
        .forEach(
            submissionBase -> {
              BigDecimal assessedTotal = assessedTotalAmounts.get(submissionBase.getSubmissionId());
              BigDecimal calcTotalAmount =
                  calculatedTotalAmounts.get(submissionBase.getSubmissionId());

              submissionBase.setAssessedTotalAmount(
                  BigDecimalUtils.scaleNullable(assessedTotal, DECIMAL_PLACES));
              submissionBase.setCalculatedTotalAmount(
                  BigDecimalUtils.scaleNullable(calcTotalAmount, DECIMAL_PLACES));
            });

    return resultSet;
  }

  private void publishValidationSucceededAfterCommit(
      UUID submissionId, SubmissionEventType eventType) {
    TransactionalPublisher.runAfterCommit(
        () ->
            submissionEventPublisherService.publishSubmissionValidationSucceededEvent(
                submissionId, eventType));
  }

  /**
   * Returns Calculated total amounts for the given submissions.
   *
   * <p>For each submission ID provided, this method retrieves the summed {@code totalAmount} from
   * the cfd record for each claim belonging to that submission. If the input list is {@code null}
   * or empty, this method returns an empty map.
   *
   * <p>The returned map is keyed by submission ID, with each value representing the Calculated
   * total amount for that submission. Submissions with no cfd records will not be present in the
   * returned map.
   *
   * @param submissionIds the unique identifiers of the submissions
   * @return a map of submission IDs to Calculated total amounts, or an empty map if the input is
   *     {@code null} or empty
   */
  public Map<UUID, BigDecimal> getCalculatedTotalAmounts(List<UUID> submissionIds) {
    if (CollectionUtils.isEmpty(submissionIds)) {
      return Map.of();
    }

    return submissionRepository.getCalculatedTotalAmounts(submissionIds).stream()
        .filter(p -> p.getSubmissionId() != null && p.getTotal() != null) // Filter out the nulls
        .collect(
            Collectors.toMap(
                SubmissionRepository.CalculatedTotalAmountProjection::getSubmissionId,
                SubmissionRepository.CalculatedTotalAmountProjection::getTotal));
  }
}
