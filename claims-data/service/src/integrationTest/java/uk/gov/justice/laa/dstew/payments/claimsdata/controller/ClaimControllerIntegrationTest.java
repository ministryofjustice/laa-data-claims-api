package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ClaimControllerIntegrationTest extends AbstractIntegrationTest {

  @BeforeEach
  void setup() {
    setupRepositories();
  }

  @Test
  void shouldReturnNotFoundWhenSubmissionIdAndClaimIdDoNotExist() throws Exception {
    mockMvc
        .perform(
            get(
                    API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}",
                    UUID.randomUUID(),
                    UUID.randomUUID())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnAClaimWhenASubmissionAndClaimExists() throws Exception {

    MvcResult result =
        mockMvc
            .perform(
                get(
                        API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}",
                        SUBMISSION_ID,
                        CLAIM_ID_1)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();
    String responseBody = result.getResponse().getContentAsString();
    var claimResponse = OBJECT_MAPPER.readValue(responseBody, ClaimResponse.class);
    assertThat(claimResponse.getId()).isEqualTo(CLAIM_ID_1.toString());
  }
}
