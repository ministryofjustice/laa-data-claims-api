package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.GET_VALIDATION_MESSAGES_PATH;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.POLL_INTERVAL;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.VALIDATION_POLL_TIMEOUT;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.BddBeansConfiguration.BddServerInfo;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;

/** End-to-end support for asserting async validation messages via real HTTP polling. */
public class BddValidationMessageStepSupport {

  @Autowired private RestTemplate restTemplate;
  @Autowired private BddServerInfo serverInfo;
  @Autowired private SubmissionRepository submissionRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public void assertSubmissionErrorExists(UUID submissionId, String expectedErrorMessage)
      throws Exception {
    long deadline = System.nanoTime() + VALIDATION_POLL_TIMEOUT.toNanos();
    String expectedLower = expectedErrorMessage.toLowerCase();

    HttpHeaders headers = new HttpHeaders();
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);
    String url =
        serverInfo.baseUrl() + GET_VALIDATION_MESSAGES_PATH + "?submission-id=" + submissionId;

    while (System.nanoTime() < deadline) {
      ResponseEntity<String> response =
          restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

      assertThat(response.getStatusCode().value())
          .as("Validation messages endpoint should return 200")
          .isEqualTo(200);

      JsonNode jsonResponse = objectMapper.readTree(response.getBody());
      JsonNode validationMessages = jsonResponse.path("content");

      if (validationMessages.isArray() && !validationMessages.isEmpty()) {
        for (JsonNode messageNode : validationMessages) {
          String displayMessage = messageNode.path("display_message").asText("").toLowerCase();
          if (displayMessage.contains(expectedLower)) {
            return;
          }
        }
      }

      Thread.sleep(POLL_INTERVAL.toMillis());
    }

    throw new AssertionError(
        String.format(
            "Expected validation messages containing '%s' for submission %s within %d ms",
            expectedErrorMessage, submissionId, VALIDATION_POLL_TIMEOUT.toMillis()));
  }

  public void assertSubmissionExists(UUID submissionId) {
    long deadline = System.nanoTime() + VALIDATION_POLL_TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      if (submissionRepository.findById(submissionId).isPresent()) {
        return;
      }
      try {
        Thread.sleep(POLL_INTERVAL.toMillis());
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted while waiting for submission persistence", interruptedException);
      }
    }

    assertThat(submissionRepository.findById(submissionId))
        .as("Expected submission %s to exist in database", submissionId)
        .isPresent();
  }

  public void assertFirstSubmissionIdAvailable() {
    // Placeholder: real verification driven from BddScenarioContext when needed.
  }
}
