package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.MatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.MatterStartMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStarterResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.MatterStartRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.lookup.AbstractEntityLookup;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/** Service containing business logic for handling matter starts. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatterStartService
    implements AbstractEntityLookup<Submission, SubmissionRepository, SubmissionNotFoundException> {

  private final SubmissionRepository submissionRepository;
  private final MatterStartRepository matterStartRepository;
  private final MatterStartMapper matterStartMapper;

  @Override
  public SubmissionRepository lookup() {
    return submissionRepository;
  }

  @Override
  public Supplier<SubmissionNotFoundException> entityNotFoundSupplier(String message) {
    return () -> new SubmissionNotFoundException(message);
  }

  /**
   * Create a matter start for a submission.
   *
   * @param submissionId submission identifier
   * @param matterStartPost request payload
   * @return identifier of the created matter start
   */
  @Transactional
  public UUID createMatterStart(UUID submissionId, MatterStartPost matterStartPost) {
    Submission submission = requireEntity(submissionId);

    MatterStart matterStart = matterStartMapper.toMatterStart(matterStartPost);
    matterStart.setId(Uuid7.timeBasedUuid());
    matterStart.setSubmission(submission);
    matterStart.setCreatedByUserId(matterStartPost.getCreatedByUserId());
    matterStartRepository.save(matterStart);
    return matterStart.getId();
  }

  /**
   * Retrieve matter start identifiers for a submission.
   *
   * @param submissionId submission identifier
   * @return list of matter start ids
   */
  @Transactional(readOnly = true)
  public List<UUID> getMatterStartIdsForSubmission(UUID submissionId) {
    return matterStartRepository.findBySubmissionId(submissionId).stream()
        .map(MatterStart::getId)
        .toList();
  }

  /**
   * Retrieve a matter start for a submission.
   *
   * @param submissionId submission identifier
   * @param matterStartId matter starts identifier
   * @return the matter start
   */
  @Transactional(readOnly = true)
  public Optional<MatterStartGet> getMatterStart(UUID submissionId, UUID matterStartId) {
    return matterStartRepository
        .findBySubmissionIdAndId(submissionId, matterStartId)
        .map(matterStartMapper::toMatterStartGet);
  }

  /**
   * Retrieve all matter starts for a specific submission ID.
   *
   * @param submissionId the identifier of the submission
   * @return a result set of matter starts, or empty if none exist
   */
  @Transactional(readOnly = true)
  public MatterStarterResultSet getAllMatterStartsForSubmission(final UUID submissionId) {
    requireEntity(submissionId);
    final List<MatterStart> matterStartEntity =
        matterStartRepository.findBySubmissionId(submissionId);

    return MatterStarterResultSet.builder()
        .submissionId(submissionId)
        .matterStarts(matterStartEntity.stream().map(matterStartMapper::toMatterStartGet).toList())
        .build();
  }
}
