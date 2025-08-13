package uk.gov.justice.laa.dstew.payments.claimsdata.service.lookup;

import java.util.UUID;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;


/**
 * Interface that centralises the common lookup logic for {@link Submission} entities.
 */
public interface SubmissionLookup {

  /**
   * Provides the {@link SubmissionRepository} to be used by the default lookup method.
   *
   * @return the repository used to retrieve {@link Submission} entities (never {@code null})
   */
  SubmissionRepository submissionLookup();

  /**
   * Retrieves an existing {@link Submission} by its identifier or throws a
   * {@link SubmissionNotFoundException} if none exists.
   *
   * @param submissionId the unique identifier of the submission to retrieve
   * @return the matching {@link Submission}
   * @throws SubmissionNotFoundException if no submission exists with the given
   *         {@code submissionId}
   */
  default Submission requireSubmission(UUID submissionId) {
    return submissionLookup()
        .findById(submissionId)
        .orElseThrow(() -> new SubmissionNotFoundException(
            String.format("No submission found with id: %s", submissionId)));
  }
}
