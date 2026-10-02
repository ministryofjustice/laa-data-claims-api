package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_ID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;

@TestPropertySource(properties = "laa.claims.api.features.validated-pending-approval=false")
@DisplayName("Bulk submission status coercion integration tests")
class BulkSubmissionStatusCoercionIntegrationTest extends AbstractIntegrationTest {

  private static final String BULK_SUBMISSION_ENDPOINT = API_URI_PREFIX + "/bulk-submissions/{id}";

  @Test
  @DisplayName(
      "With the lifecycle disabled, updating a bulk submission to VALIDATED_PENDING_APPROVAL "
          + "persists the legacy VALIDATION_SUCCEEDED status")
  void shouldCoerceValidatedPendingApprovalStatusWhenLifecycleDisabled() throws Exception {
    createBulkSubmission();

    BulkSubmissionPatch patch =
        new BulkSubmissionPatch().status(BulkSubmissionStatus.VALIDATED_PENDING_APPROVAL);

    mockMvc
        .perform(
            patch(BULK_SUBMISSION_ENDPOINT, BULK_SUBMISSION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent());

    assertThat(bulkSubmissionRepository.findById(BULK_SUBMISSION_ID).orElseThrow().getStatus())
        .isEqualTo(BulkSubmissionStatus.VALIDATION_SUCCEEDED);
  }
}
