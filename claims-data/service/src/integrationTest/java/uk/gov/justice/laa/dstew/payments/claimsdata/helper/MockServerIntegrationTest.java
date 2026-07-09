package uk.gov.justice.laa.dstew.payments.claimsdata.helper;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mockserver.MockServerContainer;
import org.testcontainers.utility.DockerImageName;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;

/**
 * Base class for integration tests that need to stub the external HTTP calls made by the shared
 * claims-validation-core library (the Fee Scheme Platform and Provider Details APIs).
 *
 * <p>It starts a single {@link MockServerContainer} for the whole test JVM and, via {@link
 * DynamicPropertySource}, points the validation-core provider URLs at it. Tests then declare the
 * responses they expect using the {@code stub*} helpers, so the assembled validation chain can run
 * end to end against controlled responses rather than a live service or a mocked bean.
 *
 * <p>Response bodies are read from {@code src/integrationTest/resources/responses}. The MockServer
 * expectations are reset after each test so stubs never leak between tests.
 */
@Slf4j
public abstract class MockServerIntegrationTest extends AbstractIntegrationTest {

  // Validation-core external endpoints (see MockServerIntegrationTest for base-URL wiring). The
  // genuine AmendmentExternalValidationStep delegates to claims-validation-core, which calls these.
  private static final String FEE_DETAILS = "/api/v2/fee-details/";
  private static final String FEE_CALCULATION = "/api/v1/fee-calculation";
  private static final String PROVIDER_OFFICES = "/api/v1/provider-offices/";
  private static final String SCHEDULES_ENDPOINT = "/schedules";

  private static final DockerImageName MOCKSERVER_IMAGE =
      DockerImageName.parse("mockserver/mockserver:5.15.0");

  /** One container per JVM; started eagerly so {@link DynamicPropertySource} can read its URL. */
  protected static final MockServerContainer MOCK_SERVER =
      new MockServerContainer(MOCKSERVER_IMAGE)
          .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)));

  static {
    MOCK_SERVER.start();
  }

  protected MockServerClient mockServerClient;

  protected final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  /**
   * Points the validation-core provider URLs at the running MockServer. The library resolves its
   * base URLs from the {@code ${FEE_SCHEME_PLATFORM_API_URL}} / {@code ${PROVIDER_DETAILS_API_URL}}
   * placeholders (see {@code application.yml}); registering those names here makes the placeholders
   * resolve to the container, so no environment variables are required. The equivalent {@code
   * dstew.payments.validator.*} keys are set too, for completeness.
   *
   * @param registry the dynamic property registry supplied by Spring
   */
  @DynamicPropertySource
  static void validatorProperties(DynamicPropertyRegistry registry) {
    String baseUrl = MOCK_SERVER.getEndpoint();

    // The placeholder names the validation-core library resolves its base URLs from.
    registry.add("FEE_SCHEME_PLATFORM_API_URL", () -> baseUrl);
    registry.add("FEE_SCHEME_PLATFORM_API_ACCESS_TOKEN", () -> "");
    registry.add("PROVIDER_DETAILS_API_URL", () -> baseUrl);
    registry.add("PROVIDER_DETAILS_API_ACCESS_TOKEN", () -> "");

    // The bound configuration keys, for completeness.
    registry.add("dstew.payments.validator.fee-scheme-platform-api.url", () -> baseUrl);
    registry.add("dstew.payments.validator.fee-scheme-platform-api.accessToken", () -> "");
    registry.add("dstew.payments.validator.provider-details-api.url", () -> baseUrl);
    registry.add("dstew.payments.validator.provider-details-api.accessToken", () -> "");
    registry.add(
        "dstew.payments.validator.provider-details-api.authHeader", () -> "X-Authorization");
  }

  @BeforeEach
  void initMockServerClient() {
    mockServerClient = new MockServerClient(MOCK_SERVER.getHost(), MOCK_SERVER.getServerPort());
  }

  @AfterEach
  void resetMockServer() {
    if (mockServerClient != null) {
      mockServerClient.reset();
    }
  }

  // ---------------------------------------------------------------------------
  // Stub helpers - start simple; add endpoint-specific helpers as tests need them.
  // ---------------------------------------------------------------------------

  /**
   * Stubs a {@code GET} on the given path to return {@code 200} with the JSON body read from the
   * named response file.
   *
   * @param path the request path to match
   * @param responseFile the response file under {@code responses/}
   * @throws IOException if the response file cannot be read
   */
  protected void stubGet(String path, String responseFile) throws IOException {
    stubGet(path, List.of(), responseFile);
  }

  /**
   * Stubs a {@code GET} on the given path and query parameters to return {@code 200} with the JSON
   * body read from the named response file.
   *
   * @param path the request path to match
   * @param queryParameters the query parameters to match (may be empty)
   * @param responseFile the response file under {@code responses/}
   * @throws IOException if the response file cannot be read
   */
  protected void stubGet(String path, List<Parameter> queryParameters, String responseFile)
      throws IOException {
    mockServerClient
        .when(
            HttpRequest.request()
                .withMethod(HttpMethod.GET.name())
                .withPath(path)
                .withQueryStringParameters(queryParameters))
        .respond(okJson(readJsonFromFile(responseFile)));
  }

  /**
   * Stubs a {@code POST} on the given path to return {@code 200} with the JSON body read from the
   * named response file.
   *
   * @param path the request path to match
   * @param responseFile the response file under {@code responses/}
   * @throws IOException if the response file cannot be read
   */
  protected void stubPost(String path, String responseFile) throws IOException {
    mockServerClient
        .when(HttpRequest.request().withMethod(HttpMethod.POST.name()).withPath(path))
        .respond(okJson(readJsonFromFile(responseFile)));
  }

  /**
   * Stubs any method on the given path to return the supplied status code with no body - useful for
   * exercising error handling.
   *
   * @param method the HTTP method to match
   * @param path the request path to match
   * @param statusCode the status code to return
   */
  protected void stubStatus(HttpMethod method, String path, int statusCode) {
    mockServerClient
        .when(HttpRequest.request().withMethod(method.name()).withPath(path))
        .respond(HttpResponse.response().withStatusCode(statusCode));
  }

  protected static HttpResponse okJson(String body) {
    return HttpResponse.response()
        .withStatusCode(200)
        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .withBody(json(body));
  }

  protected static HttpResponse okJsonFile(String responseFile) throws IOException {
    return okJson(readJsonFromFile(responseFile));
  }

  /**
   * Reads a response body from {@code src/integrationTest/resources/responses}.
   *
   * @param fileName the file name (relative to the {@code responses} directory)
   * @return the file contents
   * @throws IOException if the file cannot be read
   */
  protected static String readJsonFromFile(String fileName) throws IOException {
    String resourcePath = "responses/" + fileName;

    // Load fixtures from the classpath - fail fast with a clear message if the resource
    // is not available. This avoids brittle working-directory-dependent behaviour when
    // running tests from an IDE or alternative project root.
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    try (InputStream is = cl.getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException(
            "Response resource not found on classpath: '"
                + resourcePath
                + "'. Ensure 'src/integrationTest/resources' is configured as resources for the integrationTest sourceSet and is on the test classpath.");
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /**
   * Stubs the validation-core external calls so the genuine {@code AmendmentExternalValidationStep}
   * can run without failing on unresolved URLs. This test asserts only the forced error and the
   * "nothing persisted" invariant, so the stubbed responses just need to let the external call
   * succeed; any external validation issues they might raise do not affect those assertions.
   */
  protected void stubExternalValidationEndpoints() throws IOException {
    // Match any fee code / office as the amended payload's values vary.
    mockServerClient
        .when(request().withMethod(HttpMethod.GET.name()).withPath(FEE_DETAILS + ".*"))
        .respond(okJsonFile("fee-scheme/get-fee-details-200.json"));
    mockServerClient
        .when(request().withMethod(HttpMethod.POST.name()).withPath(FEE_CALCULATION))
        .respond(okJsonFile("fee-scheme/post-fee-calculation-200.json"));
    mockServerClient
        .when(
            request()
                .withMethod(HttpMethod.GET.name())
                .withPath(PROVIDER_OFFICES + ".*" + SCHEDULES_ENDPOINT))
        .respond(okJsonFile("provider-details/get-firm-schedules-openapi-200.json"));
  }
}
