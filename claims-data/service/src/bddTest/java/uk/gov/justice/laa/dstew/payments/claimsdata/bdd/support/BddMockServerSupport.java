package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.CucumberSpringConfiguration;

/**
 * BDD-side wrapper around the shared MockServer container started by {@link
 * CucumberSpringConfiguration#MOCK_SERVER}. Provides stub / verify helpers for the two external
 * HTTP APIs the claims-validation-core library calls (Provider Details API PDA schedules, and Fee
 * Scheme Platform), so DSTEW-1646 / DSTEW-1773 / DSTEW-1774 scenarios can drive real outbound HTTP
 * responses and inspect real request counts.
 *
 * <p>Fixture JSON files live under {@code src/integrationTest/resources/responses} and are picked
 * up off the {@code bddTest} runtime classpath (the {@code bddTest} sourceSet extends {@code
 * integrationTest.output} — see the service {@code build.gradle}).
 */
@Slf4j
public class BddMockServerSupport {

  private static final String FEE_DETAILS = "/api/v2/fee-details/";
  private static final String FEE_CALCULATION = "/api/v1/fee-calculation";
  private static final String PROVIDER_OFFICES = "/api/v1/provider-offices/";
  private static final String SCHEDULES_ENDPOINT = "/schedules";
  private static final String SCHEDULES_PATH_REGEX = PROVIDER_OFFICES + ".*" + SCHEDULES_ENDPOINT;

  private MockServerClient client;

  @PostConstruct
  void connect() {
    client =
        new MockServerClient(
            CucumberSpringConfiguration.MOCK_SERVER.getHost(),
            CucumberSpringConfiguration.MOCK_SERVER.getServerPort());
  }

  /**
   * Clears all recorded expectations / requests. Invoked from {@code BddHooks} before scenarios.
   */
  public void reset() {
    if (client != null) {
      client.reset();
    }
  }

  // ---------------------------------------------------------------------------
  // Fee Scheme Platform stubs
  // ---------------------------------------------------------------------------

  public void stubFeeSchemeEndpointsOk() throws IOException {
    client
        .when(request().withMethod(HttpMethod.GET.name()).withPath(FEE_DETAILS + ".*"))
        .respond(okJson(readJsonFromFile("fee-scheme/get-fee-details-200.json")));
    client
        .when(request().withMethod(HttpMethod.POST.name()).withPath(FEE_CALCULATION))
        .respond(okJson(readJsonFromFile("fee-scheme/post-fee-calculation-200.json")));
  }

  // ---------------------------------------------------------------------------
  // Provider Details API (PDA) stubs
  // ---------------------------------------------------------------------------

  public void stubProviderSchedulesOk() throws IOException {
    stubProviderSchedules("provider-details/get-firm-schedules-openapi-200.json");
  }

  public void stubProviderSchedulesWideWindow() throws IOException {
    stubProviderSchedules("provider-details/get-firm-schedules-wide-window-200.json");
  }

  public void stubProviderSchedules(String responseFile) throws IOException {
    client
        .when(request().withMethod(HttpMethod.GET.name()).withPath(SCHEDULES_PATH_REGEX))
        .respond(okJson(readJsonFromFile(responseFile)));
  }

  public void stubProviderSchedulesStatus(int statusCode) {
    client
        .when(request().withMethod(HttpMethod.GET.name()).withPath(SCHEDULES_PATH_REGEX))
        .respond(HttpResponse.response().withStatusCode(statusCode));
  }

  public void stubProviderSchedulesConnectionDrop() {
    client
        .when(request().withMethod(HttpMethod.GET.name()).withPath(SCHEDULES_PATH_REGEX))
        .error(HttpError.error().withDropConnection(true));
  }

  public void stubProviderSchedulesRawBody(String rawBody) {
    client
        .when(request().withMethod(HttpMethod.GET.name()).withPath(SCHEDULES_PATH_REGEX))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(rawBody));
  }

  public void stubProviderSchedulesWithDelay(Duration delay) throws IOException {
    client
        .when(request().withMethod(HttpMethod.GET.name()).withPath(SCHEDULES_PATH_REGEX))
        .respond(
            okJson(readJsonFromFile("provider-details/get-firm-schedules-openapi-200.json"))
                .withDelay(TimeUnit.MILLISECONDS, delay.toMillis()));
  }

  // ---------------------------------------------------------------------------
  // Verification helpers
  // ---------------------------------------------------------------------------

  public void verifyProviderSchedulesCalled(VerificationTimes times) {
    client.verify(
        request().withMethod(HttpMethod.GET.name()).withPath(SCHEDULES_PATH_REGEX), times);
  }

  public int countProviderSchedulesCalls() {
    return client.retrieveRecordedRequests(
            request().withMethod(HttpMethod.GET.name()).withPath(SCHEDULES_PATH_REGEX))
        .length;
  }

  /**
   * Returns whether any outbound PDA {@code /schedules} call has been recorded whose path or
   * query-string contains the given substring. Used to prove that a specific office code /
   * effective date value appears — or does not appear — on the wire.
   */
  public boolean anyProviderSchedulesRequestContains(String needle) {
    for (HttpRequest recorded :
        client.retrieveRecordedRequests(
            request().withMethod(HttpMethod.GET.name()).withPath(SCHEDULES_PATH_REGEX))) {
      String path = recorded.getPath() == null ? "" : recorded.getPath().getValue();
      String query =
          recorded.getQueryStringParameters() == null
              ? ""
              : recorded.getQueryStringParameters().toString();
      if (path.contains(needle) || query.contains(needle)) {
        return true;
      }
    }
    return false;
  }

  // ---------------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------------

  private static HttpResponse okJson(String body) {
    return HttpResponse.response()
        .withStatusCode(200)
        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .withBody(json(body));
  }

  private static String readJsonFromFile(String fileName) throws IOException {
    String resourcePath = "responses/" + fileName;
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    try (InputStream is = cl.getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException(
            "Response resource not found on classpath: '"
                + resourcePath
                + "'. Ensure the integrationTest resources are on the bddTest classpath.");
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
