package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.BddBeansConfiguration.BddServerInfo;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;

/**
 * End-to-end HTTP support for BDD steps. Calls the running application over the network using
 * {@link RestTemplate} — no {@code MockMvc}.
 */
public class BddApiStepSupport {

  private static final String POST_BULK_SUBMISSION_PATH = API_URI_PREFIX + "/bulk-submissions";

  @Autowired private RestTemplate restTemplate;
  @Autowired private BddServerInfo serverInfo;
  @Autowired private BddScenarioContext context;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public void postBulkSubmissionFile(String classpathFile, String office, String userId)
      throws IOException {
    ClassPathResource resource = new ClassPathResource(classpathFile);
    final String filename = resource.getFilename();
    byte[] bytes = resource.getInputStream().readAllBytes();

    ByteArrayResource fileResource =
        new ByteArrayResource(bytes) {
          @Override
          public String getFilename() {
            return filename;
          }
        };

    HttpHeaders filePartHeaders = new HttpHeaders();
    filePartHeaders.setContentType(MediaType.parseMediaType(resolveContentType(filename)));

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", new HttpEntity<>(fileResource, filePartHeaders));

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);

    String url =
        serverInfo.baseUrl()
            + POST_BULK_SUBMISSION_PATH
            + "?userId="
            + userId
            + "&offices="
            + office;

    int statusCode;
    String responseBody;
    try {
      ResponseEntity<String> response =
          restTemplate.exchange(
              url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
      statusCode = response.getStatusCode().value();
      responseBody = response.getBody();
    } catch (HttpStatusCodeException ex) {
      // RestTemplate throws on 4xx/5xx; capture status & body so assertions can verify them.
      HttpStatusCode status = ex.getStatusCode();
      statusCode = status.value();
      responseBody = ex.getResponseBodyAsString();
    }

    context.setLastStatusCode(statusCode);
    context.setLastResponseBody(responseBody);
    hydrateIdsFromResponse(responseBody);
  }

  private String resolveContentType(String filename) {
    if (filename == null) {
      return "text/csv";
    }
    String lower = filename.toLowerCase();
    if (lower.endsWith(".xml")) {
      return "text/xml";
    }
    if (lower.endsWith(".txt")) {
      return "text/plain";
    }
    return "text/csv";
  }

  public void assertLastResponseStatus(int expectedStatus) {
    assertThat(context.getLastStatusCode())
        .as("Expected previous API response status")
        .isEqualTo(expectedStatus);
  }

  public void assertBulkSubmissionIdPresent() {
    assertThat(context.getBulkSubmissionId()).isNotNull();
  }

  public void assertBulkSubmissionRequestCount(int expectedCount) {
    assertThat(context.getBulkSubmissionIds()).hasSize(expectedCount);
  }

  public void assertAllBulkSubmissionIdsAreUnique() {
    assertThat(context.getBulkSubmissionIds()).doesNotHaveDuplicates();
  }

  public void assertSubmissionCount(int expectedCount) {
    assertThat(context.getSubmissionIds()).hasSize(expectedCount);
  }

  private void hydrateIdsFromResponse(String responseBody) throws IOException {
    if (responseBody == null || responseBody.isBlank()) {
      return;
    }
    JsonNode json = objectMapper.readTree(responseBody);

    JsonNode bulkSubmissionNode = json.path("bulk_submission_id");
    if (!bulkSubmissionNode.isMissingNode() && !bulkSubmissionNode.isNull()) {
      UUID bulkSubmissionId = UUID.fromString(bulkSubmissionNode.asText());
      context.setBulkSubmissionId(bulkSubmissionId);
      context.getBulkSubmissionIds().add(bulkSubmissionId);
    }

    JsonNode submissionIdsNode = json.path("submission_ids");
    if (submissionIdsNode.isArray()) {
      context.getSubmissionIds().clear();
      for (JsonNode submissionIdNode : submissionIdsNode) {
        context.getSubmissionIds().add(UUID.fromString(submissionIdNode.asText()));
      }
    }
  }
}

