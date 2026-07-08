package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.BULK_STATUS_POLL_TIMEOUT;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.BULK_SUBMISSION_SUMMARY_PATH;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.BULK_TERMINAL_STATES;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.CREATE_CLAIM_PATH;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.CREATE_SUBMISSION_PATH;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.GET_BULK_SUBMISSION_BY_ID_PATH;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.GET_SUBMISSIONS_PATH;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.GET_SUBMISSION_BY_ID_PATH;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.PATCH_BULK_SUBMISSION_PATH;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.POLL_INTERVAL;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.POST_BULK_SUBMISSION_PATH;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.VOID_CLAIM_PATH;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;

/**
 * End-to-end HTTP support for BDD steps. Calls the running application over the network using
 * {@link RestTemplate} — no {@code MockMvc}.
 */
public class BddApiStepSupport {

  @Autowired private RestTemplate restTemplate;
  @Autowired private BddServerInfo serverInfo;
  @Autowired private BddScenarioContext context;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public void postBulkSubmissionFile(String classpathFile, String office, String userId)
      throws IOException {
    ClassPathResource resource = new ClassPathResource(classpathFile);
    final String filename = resource.getFilename();
    byte[] bytes;
    try (var in = resource.getInputStream()) {
      bytes = in.readAllBytes();
    }
    sendBulkSubmission(filename, bytes, office, userId);
  }

  /**
   * Uploads a generated file (already on disk) as a multipart POST to the bulk-submissions
   * endpoint, mirroring {@code postBulkSubmissionFile} but reading from {@link Path}.
   */
  public void postBulkSubmissionFromPath(Path path, String office, String userId)
      throws IOException {
    String filename = path.getFileName().toString();
    byte[] bytes = Files.readAllBytes(path);
    sendBulkSubmission(filename, bytes, office, userId, null);
  }

  /**
   * Same as {@link #postBulkSubmissionFromPath(Path, String, String)} but lets the caller force a
   * specific MIME type on the multipart file part (used by MIME-validation BDD scenarios).
   */
  public void postBulkSubmissionFromPath(Path path, String office, String userId, String mimeType)
      throws IOException {
    String filename = path.getFileName().toString();
    byte[] bytes = Files.readAllBytes(path);
    sendBulkSubmission(filename, bytes, office, userId, mimeType);
  }

  private void sendBulkSubmission(String filename, byte[] bytes, String office, String userId)
      throws IOException {
    sendBulkSubmission(filename, bytes, office, userId, null);
  }

  private void sendBulkSubmission(
      String filename, byte[] bytes, String office, String userId, String overrideMimeType)
      throws IOException {
    ByteArrayResource fileResource =
        new ByteArrayResource(bytes) {
          @Override
          public String getFilename() {
            return filename;
          }
        };

    HttpHeaders filePartHeaders = new HttpHeaders();
    filePartHeaders.setContentType(
        MediaType.parseMediaType(
            overrideMimeType != null ? overrideMimeType : resolveContentType(filename)));

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
    context.setLastOffice(office);
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

  // ---------------------------------------------------------------------------
  // Async helpers (poll until parse/validation completes).
  // ---------------------------------------------------------------------------

  /**
   * Polls the {@code /bulk-submissions/{id}/summary} endpoint until the status enters a terminal
   * state (or the timeout is reached). Returns the terminal status string.
   */
  public String waitForBulkSubmissionTerminalStatus(UUID bulkSubmissionId) {
    HttpHeaders headers = new HttpHeaders();
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);
    HttpEntity<Void> request = new HttpEntity<>(headers);

    long deadline = System.nanoTime() + BULK_STATUS_POLL_TIMEOUT.toNanos();
    String lastStatus = "UNKNOWN";
    while (System.nanoTime() < deadline) {
      try {
        ResponseEntity<String> response =
            restTemplate.exchange(
                serverInfo.baseUrl() + BULK_SUBMISSION_SUMMARY_PATH,
                HttpMethod.GET,
                request,
                String.class,
                bulkSubmissionId);
        if (response.getStatusCode().is2xxSuccessful()) {
          JsonNode json = objectMapper.readTree(response.getBody());
          lastStatus = json.path("status").asText("UNKNOWN");
          if (BULK_TERMINAL_STATES.contains(lastStatus)) {
            return lastStatus;
          }
        }
      } catch (HttpStatusCodeException | IOException ignored) {
        // keep polling; transient issues such as 404 right after creation are expected
      }
      sleepQuietly();
    }
    return lastStatus;
  }

  /**
   * Fetches the persisted submission record for the most recent upload. Useful for assertions that
   * the submission entity was actually saved.
   */
  public JsonNode getSubmission(UUID submissionId) throws IOException {
    HttpHeaders headers = new HttpHeaders();
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);
    HttpEntity<Void> request = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.exchange(
            serverInfo.baseUrl() + GET_SUBMISSION_BY_ID_PATH,
            HttpMethod.GET,
            request,
            String.class,
            submissionId);
    return objectMapper.readTree(response.getBody());
  }

  /**
   * Fetches the persisted bulk-submission record. Unlike {@link #getSubmission(UUID)} this is
   * available immediately after upload (the bulk submission is the entity directly created by the
   * POST), without requiring the event-service to parse it into per-claim submission records.
   */
  public JsonNode getBulkSubmission(UUID bulkSubmissionId) throws IOException {
    HttpHeaders headers = new HttpHeaders();
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);
    HttpEntity<Void> request = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.exchange(
            serverInfo.baseUrl() + GET_BULK_SUBMISSION_BY_ID_PATH,
            HttpMethod.GET,
            request,
            String.class,
            bulkSubmissionId);
    return objectMapper.readTree(response.getBody());
  }

  /**
   * Convenience: returns the area-of-law string ({@code "LEGAL HELP"} / {@code "CRIME LOWER"} /
   * {@code "MEDIATION"}) of a persisted submission.
   */
  public String getSubmissionAreaOfLaw(UUID submissionId) throws IOException {
    return getSubmission(submissionId).path("area_of_law").asText("");
  }

  public int countSubmissionsForOffice(String office) {
    HttpHeaders headers = new HttpHeaders();
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);
    HttpEntity<Void> request = new HttpEntity<>(headers);

    String url = serverInfo.baseUrl() + GET_SUBMISSIONS_PATH + "?offices=" + office + "&size=100";
    try {
      ResponseEntity<String> response =
          restTemplate.exchange(url, HttpMethod.GET, request, String.class);
      JsonNode json = objectMapper.readTree(response.getBody());
      return json.path("content").isArray() ? json.path("content").size() : 0;
    } catch (HttpStatusCodeException | IOException ex) {
      return 0;
    }
  }

  private static void sleepQuietly() {
    try {
      Thread.sleep(POLL_INTERVAL.toMillis());
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Voids a single claim via {@code POST /api/v1/claims/{claimId}/void}. Used by the disbursement
   * duplicate-check scenarios that re-submit a claim after voiding.
   *
   * @param userId a UUID identifying the caller; the endpoint's {@code created_by_user_id} field is
   *     typed as {@link UUID} in the OpenAPI schema, so a UUID string must be supplied.
   */
  public int voidClaim(UUID claimId, UUID userId, String reason) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);

    String body =
        "{\"created_by_user_id\":\"" + userId + "\",\"assessment_reason\":\"" + reason + "\"}";

    try {
      ResponseEntity<String> response =
          restTemplate.exchange(
              serverInfo.baseUrl() + VOID_CLAIM_PATH,
              HttpMethod.POST,
              new HttpEntity<>(body, headers),
              String.class,
              claimId);
      return response.getStatusCode().value();
    } catch (HttpStatusCodeException ex) {
      // Preserve the response body in the context so the step can log/assert against it.
      context.setLastResponseBody(ex.getResponseBodyAsString());
      return ex.getStatusCode().value();
    }
  }

  /**
   * Creates a Submission entity via {@code POST /api/v1/submissions} so that follow-up steps (e.g.
   * claim creation, claim voiding) have a real DB row to act on. Normally the event-service
   * materialises submissions during file parsing; in local BDD mode we do it explicitly.
   */
  public void createSubmission(
      UUID submissionId, UUID bulkSubmissionId, String office, String submissionPeriod)
      throws IOException {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);

    String body =
        "{"
            + "\"submission_id\":\""
            + submissionId
            + "\","
            + "\"bulk_submission_id\":\""
            + bulkSubmissionId
            + "\","
            + "\"office_account_number\":\""
            + office
            + "\","
            + "\"submission_period\":\""
            + submissionPeriod
            + "\","
            + "\"area_of_law\":\"LEGAL HELP\","
            + "\"provider_user_id\":\"test-user\","
            + "\"status\":\"READY_FOR_VALIDATION\","
            + "\"created_by_user_id\":\"test-user\","
            + "\"is_nil_submission\":true,"
            + "\"number_of_claims\":0,"
            + "\"legal_help_submission_reference\":\"BDDLHREF001\""
            + "}";

    restTemplate.exchange(
        serverInfo.baseUrl() + CREATE_SUBMISSION_PATH,
        HttpMethod.POST,
        new HttpEntity<>(body, headers),
        String.class);
  }

  /**
   * Creates a minimal Legal Help claim against the given submission via {@code POST
   * /api/v1/submissions/{id}/claims} and returns the new claim's UUID.
   *
   * <p>Used by BDD scenarios running in local mode (no event-service) to materialise a claim ID so
   * downstream steps like voiding can exercise real API endpoints.
   */
  public UUID createClaim(UUID submissionId) throws IOException {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);

    // Build a coherent ClaimPost using the generated model to guarantee JSON shape matches the
    // OpenAPI schema. Values mirror the Legal Help disbursement claim style used by the file
    // generator, plus every field the ClaimsDataTestUtil.getClaimPost fixture sets, so any
    // bean-validation constraints downstream are satisfied.
    // Payload mirrors the working shape used by ClaimControllerTest.createClaim (unit-test) —
    // guarantees every field the OpenAPI bean-validation / @ScanForSql checks accepts.
    String body =
        "{"
            + "\"status\":\"VALID\","
            + "\"schedule_reference\":\"SCH-BDD\","
            + "\"line_number\":1,"
            + "\"case_reference_number\":\"CRN-BDD\","
            + "\"unique_file_number\":\"UFN-BDD\","
            + "\"case_start_date\":\"01/07/2025\","
            + "\"case_concluded_date\":\"31/07/2025\","
            + "\"matter_type_code\":\"MAT01\","
            + "\"outcome_code\":\"OUT01\","
            + "\"created_by_user_id\":\"test-user\""
            + "}";

    ResponseEntity<String> response =
        restTemplate.exchange(
            serverInfo.baseUrl() + CREATE_CLAIM_PATH,
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class,
            submissionId);

    JsonNode json = objectMapper.readTree(response.getBody());
    JsonNode idNode = json.path("id");
    if (idNode.isMissingNode() || idNode.isNull()) {
      throw new IllegalStateException(
          "POST /submissions/"
              + submissionId
              + "/claims did not return an id: "
              + response.getBody());
    }
    return UUID.fromString(idNode.asText());
  }

  // ---------------------------------------------------------------------------
  // PATCH bulk-submission (used by BDD scenarios to simulate event-service
  // driving a submission into a terminal status when the event-service is not
  // running locally).
  // ---------------------------------------------------------------------------

  /** Sets the given bulk-submission's status to {@link BulkSubmissionStatus#VALIDATION_FAILED}. */
  public void markBulkSubmissionAsInvalid(UUID bulkSubmissionId) {
    patchBulkSubmissionStatus(bulkSubmissionId, BulkSubmissionStatus.VALIDATION_FAILED);
  }

  /**
   * Forces the given bulk-submission to the requested terminal status via {@code PATCH
   * /api/v1/bulk-submissions/{id}}. Used by BDD scenarios running in local mode (no real
   * event-service) to simulate a duplicate-check outcome.
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
      Set<UUID> existing = new HashSet<>(context.getSubmissionIds());
      for (JsonNode submissionIdNode : submissionIdsNode) {
        UUID id = UUID.fromString(submissionIdNode.asText());
        if (!existing.contains(id)) {
          context.getSubmissionIds().add(id);
        }
      }
    }
  }
}
