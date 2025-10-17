package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionBadRequestException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionsResultSetMapper;
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

/** Service containing business logic for handling submissions. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionService
    implements AbstractEntityLookup<Submission, SubmissionRepository, SubmissionNotFoundException> {
  private final SubmissionRepository submissionRepository;
  private final SubmissionMapper submissionMapper;
  private final ClaimService claimService;
  private final MatterStartService matterStartService;
  private final ValidationMessageLogRepository validationMessageLogRepository;
  private final SubmissionsResultSetMapper submissionsResultSetMapper;
  private final SubmissionEventPublisherService submissionEventPublisherService;

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

    return new SubmissionResponse()
        .submissionId(submission.getId())
        .bulkSubmissionId(submission.getBulkSubmissionId())
        .officeAccountNumber(submission.getOfficeAccountNumber())
        .submissionPeriod(submission.getSubmissionPeriod())
        .areaOfLaw(submission.getAreaOfLaw())
        .status(submission.getStatus())
        .crimeScheduleNumber(submission.getCrimeScheduleNumber())
        .civilSubmissionReference(submission.getCivilSubmissionReference())
        .mediationSubmissionReference(submission.getMediationSubmissionReference())
        .previousSubmissionId(submission.getPreviousSubmissionId())
        .isNilSubmission(submission.getIsNilSubmission())
        .numberOfClaims(submission.getNumberOfClaims())
        .submitted(OffsetDateTime.ofInstant(submission.getCreatedOn(), ZoneId.systemDefault()))
        .claims(claims)
        .calculatedTotalAmount(submissionRepository.getCalculatedTotalAmount(id))
        .matterStarts(matterStartIds)
        .providerUserId(submission.getProviderUserId());
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
      submissionEventPublisherService.publishSubmissionValidationEvent(submission.getId());
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
      String areaOfLaw,
      String submissionPeriod,
      List<SubmissionStatus> submissionStatuses,
      Pageable pageable) {

    if (offices == null || offices.isEmpty()) {
      throw new SubmissionBadRequestException("Missing offices list");
    }

    Page<Submission> page =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(offices)
                .and(SubmissionSpecification.submissionIdEqualTo(submissionId))
                .and(SubmissionSpecification.createdOnOrAfter(submittedDateFrom))
                .and(SubmissionSpecification.createdOnOrBefore(submittedDateTo))
                .and(SubmissionSpecification.areaOfLawEqual(areaOfLaw))
                .and(SubmissionSpecification.submissionPeriodEqual(submissionPeriod))
                .and(SubmissionSpecification.submissionStatusIn(submissionStatuses)),
            pageable);

    return submissionsResultSetMapper.toSubmissionsResultSet(page);
  }
}
