package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.CucumberSpringConfiguration;

/** MockServer helpers reused by amendment/PDA BDD steps. */
public class PdaMockServerSupport {

  private static final String FEE_DETAILS = "/api/v2/fee-details/";
  private static final String FEE_CALCULATION = "/api/v1/fee-calculation";
  private static final String PROVIDER_OFFICES = "/api/v1/provider-offices/";
  private static final String SCHEDULES_ENDPOINT = "/schedules";
  private static final String SCHEDULES_PATH_REGEX = PROVIDER_OFFICES + ".*" + SCHEDULES_ENDPOINT;

  private MockServerClient client;

  @PostConstruct
  void init() {
    client =
        new MockServerClient(
            CucumberSpringConfiguration.MOCK_SERVER.getHost(),
            CucumberSpringConfiguration.MOCK_SERVER.getServerPort());
  }

  public void reset() {
    if (client != null) {
      client.reset();
    }
  }

  public void stubFeeSchemeEndpoints() throws IOException {
    client
        .when(request().withMethod(HttpMethod.GET.name()).withPath(FEE_DETAILS + ".*"))
        .respond(okJson(readJsonFromFile("fee-scheme/get-fee-details-200.json")));
    client
        .when(request().withMethod(HttpMethod.POST.name()).withPath(FEE_CALCULATION))
        .respond(okJson(readJsonFromFile("fee-scheme/post-fee-calculation-200.json")));
  }

  public void stubProviderSchedulesOk() throws IOException {
    stubProviderSchedules("provider-details/get-firm-schedules-wide-window-200.json");
  }

  public void stubProviderSchedules(String responseFile) throws IOException {
    client
        .when(request().withMethod(HttpMethod.GET.name()).withPath(SCHEDULES_PATH_REGEX))
        .respond(okJson(readJsonFromFile(responseFile)));
  }

  public void stubProviderSchedulesWithDelay(Duration delay) throws IOException {
    client
        .when(request().withMethod(HttpMethod.GET.name()).withPath(SCHEDULES_PATH_REGEX))
        .respond(
            okJson(readJsonFromFile("provider-details/get-firm-schedules-wide-window-200.json"))
                .withDelay(TimeUnit.MILLISECONDS, delay.toMillis()));
  }

  public void stubProviderSchedulesCategoryMismatch() throws IOException {
    stubProviderSchedules("provider-details/get-firm-schedules-category-mismatch-200.json");
  }

  public void stubProviderSchedulesNoMatchingAreaOfLaw() {
    stubProviderSchedulesRawBody(
        """
        {
          "firm": {
            "firmNumber": "string",
            "firmId": 0,
            "ccmsFirmId": 0,
            "parentFirmId": 0,
            "firmName": "string",
            "firmType": "string",
            "constitutionalStatus": "string",
            "solicitorAdvocateYN": "string",
            "advocateLevel": "string",
            "barCouncilRoll": "string",
            "companyHouseNumber": "string"
          },
          "office": {
            "firmOfficeId": 0,
            "ccmsFirmOfficeId": 0,
            "firmOfficeCode": "string",
            "officeName": "string",
            "officeCodeAlt": "string",
            "type": "string"
          },
          "pds": true,
          "schedules": [
            {
              "contractType": "string",
              "contractDescription": "string",
              "contractNumber": "string",
              "contractReference": "string",
              "contractStatus": "string",
              "contractAuthorizationStatus": "string",
              "contractStartDate": "2000-01-01",
              "contractEndDate": "2100-12-31",
              "areaOfLaw": "CRIME LOWER",
              "scheduleType": "string",
              "scheduleNumber": "string",
              "scheduleContractNumber": "string",
              "scheduleContractReference": "string",
              "scheduleAuthorizationStatus": "string",
              "scheduleStatus": "string",
              "scheduleStartDate": "2000-01-01",
              "scheduleEndDate": "2100-12-31",
              "scheduleLines": [
                {
                  "areaOfLaw": "CRIME LOWER",
                  "categoryOfLaw": "string",
                  "description": "string",
                  "devolvedPowersStatus": "string",
                  "dpTypeOfChange": "string",
                  "dpReasonForChange": "string",
                  "dpDateOfChange": "string",
                  "remainderWorkFlag": "string",
                  "minimumCasesAllowedCount": "string",
                  "maximumCasesAllowedCount": "string",
                  "minimumToleranceCount": "string",
                  "maximumToleranceCount": "string",
                  "minimumLicenseCount": "string",
                  "maximumLicenseCount": "string",
                  "workInProgressCount": "string",
                  "outreach": "string",
                  "cancelFlag": "string",
                  "cancelReason": "string",
                  "cancelDate": "2100-12-31",
                  "closedDate": "2100-12-31",
                  "closedReason": "string"
                }
              ],
              "nmsAuths": []
            }
          ]
        }
        """);
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

  public void verifyProviderSchedulesCalled(VerificationTimes times) {
    client.verify(
        request().withMethod(HttpMethod.GET.name()).withPath(SCHEDULES_PATH_REGEX), times);
  }

  public void verifyProviderSchedulesCalledForOffice(String officeCode, VerificationTimes times) {
    client.verify(
        request()
            .withMethod(HttpMethod.GET.name())
            .withPath(PROVIDER_OFFICES + officeCode + SCHEDULES_ENDPOINT),
        times);
  }

  private static HttpResponse okJson(String body) {
    return HttpResponse.response()
        .withStatusCode(200)
        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .withBody(json(body));
  }

  private static String readJsonFromFile(String fileName) throws IOException {
    String resourcePath = "responses/" + fileName;
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
      if (inputStream == null) {
        throw new IOException("Response resource not found on classpath: " + resourcePath);
      }
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
