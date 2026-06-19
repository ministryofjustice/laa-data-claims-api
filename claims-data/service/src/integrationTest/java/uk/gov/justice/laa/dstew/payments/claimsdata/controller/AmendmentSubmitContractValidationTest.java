package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class AmendmentSubmitContractValidationTest extends AbstractIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void setupMockMvcBypassingSecurity() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  private static final String ENDPOINT_URL =
      "/api/v1/submissions/{submission-id}/claims/{claim-id}";

  @Test
  void shouldPassValidationWhenValidIntegerVersionAndNonEmptyAmendmentsProvided() throws Exception {
    String claimId = UUID.randomUUID().toString();
    String submissionId = UUID.randomUUID().toString();

    String validPayload =
        """
            {
              "version": 7,
              "created_by_user_id": "test-user",
              "client_surname": "Smith"
            }
            """;

    mockMvc
        .perform(
            patch(ENDPOINT_URL, submissionId, claimId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validPayload))
        // 404 PROVES IT PASSED THE @Valid GATE AND REACHED THE CONTROLLER DB QUERY!
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldPassValidationWhenAmendmentsBlockIsEmpty() throws Exception {
    String claimId = UUID.randomUUID().toString();
    String submissionId = UUID.randomUUID().toString();

    // Since ClaimPatch is a flat object, sending just the version is structurally valid JSON.
    String emptyAmendmentsPayload =
        """
            {
              "version": 7,
              "created_by_user_id": "test-user"
            }
            """;

    mockMvc
        .perform(
            patch(ENDPOINT_URL, submissionId, claimId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(emptyAmendmentsPayload))
        .andExpect(status().isNotFound()); // Proves it passes JSON syntax validation
  }

  @Test
  void shouldReturn400BadRequestWhenVersionIsAbsent() throws Exception {
    String claimId = UUID.randomUUID().toString();
    String submissionId = UUID.randomUUID().toString();

    // The 'version' field is completely omitted, triggering the @NotNull exception
    String missingVersionPayload =
        """
            {
              "created_by_user_id": "test-user",
              "client_surname": "Smith"
            }
            """;

    mockMvc
        .perform(
            patch(ENDPOINT_URL, submissionId, claimId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(missingVersionPayload))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"5F\"", "\"\"", "null", "[]", "{}", "5F"})
  void shouldReturn400BadRequestWhenVersionIsInvalid(String invalidVersionValue) throws Exception {
    String claimId = UUID.randomUUID().toString();
    String submissionId = UUID.randomUUID().toString();

    String invalidPayload =
        """
            {
              "version": VERSION_PLACEHOLDER,
              "created_by_user_id": "test-user",
              "client_surname": "Smith"
            }
            """
            .replace("VERSION_PLACEHOLDER", invalidVersionValue);

    mockMvc
        .perform(
            patch(ENDPOINT_URL, submissionId, claimId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
        .andExpect(status().isBadRequest());
  }
}
