package uk.gov.justice.laa.dstew.payments.claimsdata.service.lookup;

import java.util.UUID;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;

/**
 * Interface that centralises the common lookup logic for {@link BulkSubmission} entities.
 */
public interface BulkSubmissionLookup {
  /**
   * Provides the {@link BulkSubmissionRepository} to be used by the default lookup method.
   *
   * @return the repository used to retrieve {@link BulkSubmission} entities (never {@code null})
   */
  BulkSubmissionRepository bulkSubmissionLookup();

  /**
   * Retrieves an existing {@link BulkSubmission} by its identifier or throws a
   * {@link BulkSubmissionNotFoundException} if none exists.
   *
   * @param bulkSubmissionId the unique identifier of the bulk submission to retrieve
   * @return the matching {@link BulkSubmission}
   * @throws BulkSubmissionNotFoundException if no bulk submission exists with the given
   *         {@code bulkSubmissionId}
   */
  default BulkSubmission requireBulkSubmission(UUID bulkSubmissionId) {
    return bulkSubmissionLookup()
            .findById(bulkSubmissionId)
            .orElseThrow(() -> new BulkSubmissionNotFoundException(
                    String.format("No bulk submission found with id: %s", bulkSubmissionId)));
  }
}
