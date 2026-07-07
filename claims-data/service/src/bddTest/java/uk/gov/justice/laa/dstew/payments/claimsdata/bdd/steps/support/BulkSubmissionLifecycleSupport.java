package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support;

import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.PATCH_BULK_SUBMISSION_PATH;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.BddBeansConfiguration.BddServerInfo;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;

/**
 * Thin wrapper over {@code PATCH /bulk-submissions/{id}} used by BDD scenarios to simulate the
 * effect of the event-service marking a submission as {@code VALIDATION_FAILED}. The BDD harness
 * doesn't run the event-service, so we drive the state transition directly via the public API.
 */
public class BulkSubmissionLifecycleSupport {

  @Autowired private RestTemplate restTemplate;
  @Autowired private BddServerInfo serverInfo;

  /** Sets the given bulk-submission's status to {@link BulkSubmissionStatus#VALIDATION_FAILED}. */
  public void markBulkSubmissionAsInvalid(UUID bulkSubmissionId) {
    patchBulkSubmissionStatus(bulkSubmissionId, BulkSubmissionStatus.VALIDATION_FAILED);
  }

  /**
   * Forces the given bulk-submission to the requested terminal status. Used by BDD scenarios
   * running in local mode (no real event-service) to simulate a duplicate-check outcome.
   */
  public void patchBulkSubmissionStatus(UUID bulkSubmissionId, BulkSubmissionStatus status) {
    BulkSubmissionPatch patch = new BulkSubmissionPatch();
    patch.setBulkSubmissionId(bulkSubmissionId);
    patch.setStatus(status);
    patch.setUpdatedByUserId("bdd-mock-event-service");

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);

    restTemplate.exchange(
        serverInfo.baseUrl() + PATCH_BULK_SUBMISSION_PATH,
        HttpMethod.PATCH,
        new HttpEntity<>(patch, headers),
        Void.class,
        bulkSubmissionId);
  }
}
