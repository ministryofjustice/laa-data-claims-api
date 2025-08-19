package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.gov.justice.laa.dstew.payments.claimsdata.ClaimsDataApplication;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.SqsTestConfig;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;

@ActiveProfiles("test")
@SpringBootTest(classes = ClaimsDataApplication.class)
@AutoConfigureMockMvc
@Transactional
@Testcontainers
@Slf4j
@Import(SqsTestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ClaimControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired
  private BulkSubmissionRepository bulkSubmissionRepository;

  @Container @ServiceConnection
  public static final PostgreSQLContainer<?> postgresContainer =
      new PostgreSQLContainer<>("postgres:latest");

  private static final String AUTHORIZATION_HEADER = "Authorization";

  // must match application-test.yml for test-runner token
  private static final String AUTHORIZATION_TOKEN = "f67f968e-b479-4e61-b66e-f57984931e56";

  //TODO: DSTEW-321 add more scenarios & add sql scripts to populate db with test data
  @ParameterizedTest(name = """
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
