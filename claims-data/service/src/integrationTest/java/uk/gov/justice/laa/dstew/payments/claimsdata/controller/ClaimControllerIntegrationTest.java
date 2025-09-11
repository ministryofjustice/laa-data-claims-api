package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;

import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;

public class ClaimControllerIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private BulkSubmissionRepository bulkSubmissionRepository;

  private static final String AUTHORIZATION_HEADER = "Authorization";

  // must match application-test.yml for test-runner token
  private static final String AUTHORIZATION_TOKEN = "f67f968e-b479-4e61-b66e-f57984931e56";

  // TODO: DSTEW-321 add more scenarios & add sql scripts to populate db with test data
  @ParameterizedTest(
      name =
          """
      GIVEN submissionId={0} and claimId={1}
      WHEN requesting a claim
      THEN the response status is {2}
      """)
  @CsvSource({
    // submissionId, claimId, expectedStatus
    "32765fbb-b258-4c20-a212-b68085843590, 49c5bc98-9b64-4f34-a2f6-861f06c1b95a, 404",
  })
  void shouldRequestClaimWithStatus(UUID submissionId, UUID claimId, int expectedStatus)
      throws Exception {
    mockMvc
        .perform(
            get(
                    API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}",
                    submissionId,
                    claimId)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().is(expectedStatus));
  }
}
