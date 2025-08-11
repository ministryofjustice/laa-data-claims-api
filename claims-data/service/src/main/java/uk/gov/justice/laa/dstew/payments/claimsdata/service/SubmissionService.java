package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetSubmission200Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetSubmission200ResponseClaimsInner;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;

/**
 * Service containing business logic for handling submissions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionService {
  private final SubmissionRepository submissionRepository;
  private final SubmissionMapper submissionMapper;
  private final ClaimService claimService;
  private final MatterStartService matterStartService;

  /**
   * Create and persist a new submission.
   *
   * @param submissionPost request body
   * @return id of the created submission
   */
  public UUID createSubmission(SubmissionPost submissionPost) {
    Submission submission = submissionMapper.toSubmission(submissionPost);
    //  TODO: replace with the actual user ID/name when available
    submission.setCreatedByUserId("todo");

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
  public GetSubmission200Response getSubmission(UUID id) {
    Submission submission =
        submissionRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new SubmissionNotFoundException(
                        String.format("No submission found with id: %s", id)));

    SubmissionFields fields = submissionMapper.toSubmissionFields(submission);

    List<GetSubmission200ResponseClaimsInner> claims =
        claimService.getClaimsForSubmission(id);

    List<UUID> matterStartIds = matterStartService.getMatterStartIdsForSubmission(id);

    return new GetSubmission200Response()
        .submission(fields)
        .claims(claims)
        .matterStarts(matterStartIds);
  }

  /**
   * Partially update a submission.
   *
   * @param id the submission id
   * @param submissionPatch patch object containing updated fields
   */
  @Transactional
  public void updateSubmission(UUID id, SubmissionPatch submissionPatch) {
    Submission submission =
        submissionRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new SubmissionNotFoundException(
                        String.format("No submission found with id: %s", id)));

    submissionMapper.updateSubmissionFromPatch(submissionPatch, submission);
    submissionRepository.save(submission);
  }
}
