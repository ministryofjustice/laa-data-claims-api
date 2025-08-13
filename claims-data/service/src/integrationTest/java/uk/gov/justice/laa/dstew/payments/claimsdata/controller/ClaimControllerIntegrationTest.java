package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.ClaimsDataApplication;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@SpringBootTest(classes = ClaimsDataApplication.class)
@AutoConfigureMockMvc
@Transactional
@Testcontainers
@Slf4j
public class ClaimControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Container
  @ServiceConnection
  public static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:latest");

  private static final String AUTHORIZATION_HEADER = "Authorization";

  //must match application-test.yml for test-runner token
  private static final String AUTHORIZATION_TOKEN = "f67f968e-b479-4e61-b66e-f57984931e56";


  //todo add more scenarios & add sql scripts to populate db with test data
  @ParameterizedTest(name = """
      GIVEN submissionId={0} and claimId={1}
      WHEN requesting a claim
      THEN the response status is {2}
      """)
  @CsvSource({
      // submissionId, claimId, expectedStatus
      "32765fbb-b258-4c20-a212-b68085843590, 49c5bc98-9b64-4f34-a2f6-861f06c1b95a, 404",
  })
  void shouldRequestClaim_withStatus(
      UUID submissionId,
      UUID claimId,
      int expectedStatus
  ) throws Exception {
    mockMvc.perform(get("/api/v0/submissions/{submissionId}/claims/{claimId}", submissionId, claimId)
            .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().is(expectedStatus));
  }
}
