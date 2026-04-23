package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionBadRequestException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.service.lookup.AbstractEntityLookup;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.BigDecimalUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.TransactionalPublisher;

/** Service containing business logic for handling submissions. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionService
    implements AbstractEntityLookup<Submission, SubmissionRepository, SubmissionNotFoundException> {
  public static final short DECIMAL_PLACES = 2;

  private static final Set<String> ALLOWED_SORT_FIELDS =
      Set.of("createdOn", "officeAccountNumber", "areaOfLaw", "submissionPeriod", "status");

  private final SubmissionRepository submissionRepository;
  private final SubmissionMapper submissionMapper;
  private final ClaimService claimService;
  private final MatterStartService matterStartService;
  private final ValidationMessageLogRepository validationMessageLogRepository;
  private final SubmissionsResultSetMapper submissionsResultSetMapper;
  private final SubmissionEventPublisherService submissionEventPublisherService;
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

    submissionRepository.save(submission);
    return submission.getId();
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
        .submitted(OffsetDateTime.ofInstant(submission.getCreatedOn(), ZoneId.systemDefault()))
        .claims(claims)
        .calculatedTotalAmount(BigDecimalUtils.scaleOrZero(calculatedTotalAmount, DECIMAL_PLACES))
        .assessedTotalAmount(BigDecimalUtils.scaleNullable(assessedTotalAmount, DECIMAL_PLACES))
        .matterStarts(matterStartIds)
        .createdByUserId(submission.getCreatedByUserId())
        .providerUserId(submission.getProviderUserId())
        .errorMessages(submission.getErrorMessages());
  }

  /**
   * Partially update a submission.
   *
   * @param id the submission id
   * @param submissionPatch patch object containing updated fields
   */
  @Transactional
  public void updateSubmission(UUID id, SubmissionPatch submissionPatch) {
    Submission submission = requireEntity(id);

    submissionMapper.updateSubmissionFromPatch(submissionPatch, submission);
    submissionRepository.save(submission);

    if (submissionPatch.getStatus() == SubmissionStatus.READY_FOR_VALIDATION) {
      TransactionalPublisher.runAfterCommit(
          () ->
              submissionEventPublisherService.publishSubmissionValidationEvent(submission.getId()));
    } else if (submissionPatch.getStatus() == SubmissionStatus.VALIDATION_FAILED) {
      int totalUpdatedClaims =
          claimService.updateAllClaimsStatusForSubmission(id, ClaimStatus.INVALID);
      log.debug("Updated {} claims to INVALID status for submission {}", totalUpdatedClaims, id);
    }

    if (submissionPatch.getValidationMessages() != null
        && !submissionPatch.getValidationMessages().isEmpty()) {
      submissionPatch
          .getValidationMessages()
          .forEach(
              message -> {
                ValidationMessageLog log =
                    submissionMapper.toValidationMessageLog(message, submission);
                validationMessageLogRepository.save(log);
              });
    }
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

    Pageable stablePageable = validateAndRemapPageable(pageable);

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

    resultSet
        .getContent()
        .forEach(
            submissionBase -> {
              BigDecimal assessedTotal = assessedTotalAmounts.get(submissionBase.getSubmissionId());

              submissionBase.setAssessedTotalAmount(
                  BigDecimalUtils.scaleNullable(assessedTotal, DECIMAL_PLACES));
            });

    return resultSet;
  }

  /**
   * Validates that all sort properties in the given {@link Pageable} are in the allowed set, then
   * returns a new {@link Pageable} with sort field names remapped to their internal equivalents:
   *
   * <ul>
   *   <li>{@code submissionPeriod} → {@code submissionPeriodSortKey} (chronological YYYYMM order)
   *   <li>{@code officeAccountNumber} → {@code officeAccountNumberSortKey} (case-insensitive)
   * </ul>
   *
   * <p>A stable tie-breaking secondary sort by {@code id} is always appended, using the same
   * direction as the primary sort (defaulting to {@code ASC} if unsorted).
   *
   * <p>Null handling: PostgreSQL applies {@code NULLS LAST} for {@code ASC} and {@code NULLS FIRST}
   * for {@code DESC} by default. Rows with null values in a sorted column will therefore appear at
   * the end of ascending results and at the start of descending results.
   *
   * <p>@param pageable the pageable to validate and augment
   *
   * <p>@return a new pageable with remapped sort fields and a tie-breaking sort appended
   *
   * <p>@throws SubmissionBadRequestException if any sort property is not in {@link
   * #ALLOWED_SORT_FIELDS}
   */
  private Pageable validateAndRemapPageable(Pageable pageable) {
    pageable
        .getSort()
        .forEach(
            order -> {
              if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                throw new SubmissionBadRequestException(
                    "Invalid sort field: '"
                        + order.getProperty()
                        + "'. Allowed fields: "
                        + ALLOWED_SORT_FIELDS);
              }
            });

    Sort.Direction tieBreakerDirection =
        pageable.getSort().isSorted()
            ? pageable.getSort().iterator().next().getDirection()
            : Sort.Direction.ASC;

    List<Sort.Order> remappedOrders =
        pageable.getSort().stream()
            .map(
                order ->
                    switch (order.getProperty()) {
                      case "submissionPeriod" ->
                          new Sort.Order(order.getDirection(), "submissionPeriodSortKey");
                      case "officeAccountNumber" ->
                          new Sort.Order(order.getDirection(), "officeAccountNumberSortKey");
                      default -> order;
                    })
            .toList();

    Sort sortWithTieBreaker = Sort.by(remappedOrders).and(Sort.by(tieBreakerDirection, "id"));
    int pageNumber = pageable.isPaged() ? pageable.getPageNumber() : 0;
    int pageSize = pageable.isPaged() ? pageable.getPageSize() : 20;
    return PageRequest.of(pageNumber, pageSize, sortWithTieBreaker);
  }
}
