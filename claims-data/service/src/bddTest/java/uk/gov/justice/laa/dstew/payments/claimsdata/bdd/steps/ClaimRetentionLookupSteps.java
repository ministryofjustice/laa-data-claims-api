package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.BddBeansConfiguration.BddServerInfo;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/** Feature-scoped steps for the retention lookup endpoint. */
public class ClaimRetentionLookupSteps {

  private static final String OFFICE_CODE = "RCW001";
  private static final String UFN = "230493/001";
  private static final String ENDPOINT = "/api/v1/claims/retention-lookup";
  private static final String CREATED_BY = "bdd-retention-lookup";
  private static final Set<String> CLAIM_FIELDS =
      Set.of("claim_id", "status", "created_on", "updated_on");

  @Autowired private ClaimRepository claimRepository;
  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private RestTemplate restTemplate;
  @Autowired private BddServerInfo serverInfo;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final List<Claim> seededClaims = new ArrayList<>();
  private JsonNode lastResponse;

  @Given("the retention lookup endpoint is available")
  public void retentionLookupEndpointIsAvailable() {
    step(
        "confirm the retention lookup endpoint base URL is available",
        () -> assertThat(serverInfo.baseUrl()).isNotBlank());
  }

  @Given("no claims match the retention lookup criteria")
  public void noClaimsMatchRetentionLookupCriteria() {
    step(
        "start with no seeded claims for the retention lookup criteria",
        () -> assertThat(seededClaims).isEmpty());
  }

  @Given("{int} VALID claim(s) match(es) the retention lookup criteria")
  public void validClaimsMatchRetentionLookupCriteria(int count) {
    step(
        "persist " + count + " VALID claims with the retention lookup office code and UFN",
        () -> {
          Submission submission =
              submissionRepository.saveAndFlush(
                  Submission.builder()
                      .id(Uuid7.timeBasedUuid())
                      .officeAccountNumber(OFFICE_CODE)
                      .submissionPeriod("JAN-2025")
                      .areaOfLaw(AreaOfLaw.LEGAL_HELP)
                      .status(SubmissionStatus.VALIDATION_SUCCEEDED)
                      .createdByUserId(CREATED_BY)
                      .providerUserId(CREATED_BY)
                      .createdOn(Instant.now())
                      .build());

          for (int lineNumber = 1; lineNumber <= count; lineNumber++) {
            seededClaims.add(
                claimRepository.saveAndFlush(
                    Claim.builder()
                        .id(Uuid7.timeBasedUuid())
                        .submission(submission)
                        .uniqueFileNumber(UFN)
                        .status(ClaimStatus.VALID)
                        .lineNumber(lineNumber)
                        .matterTypeCode("RETENTION")
                        .createdByUserId(CREATED_BY)
                        .build()));
          }
        });
  }

  @When("I look up the matching retention claims")
  public void lookUpMatchingRetentionClaims() {
    step(
        "GET the retention lookup for the matching claims",
        () -> {
          String url =
              serverInfo.baseUrl()
                  + ENDPOINT
                  + "?office_code="
                  + OFFICE_CODE
                  + "&ufn="
                  + UFN
                  + "&page=0&size=20";

          HttpHeaders headers = new HttpHeaders();
          headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);
          headers.setAccept(List.of(MediaType.APPLICATION_JSON));
          ResponseEntity<String> response =
              restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
          assertThat(response.getStatusCode().value()).isEqualTo(200);
          lastResponse = objectMapper.readTree(response.getBody());
        });
  }

  @Then("the retention response contains {int} matching claims in {int} pages")
  public void retentionResponseContainsMatchingClaims(int totalElements, int totalPages) {
    step(
        "verify retention lookup totals and the first page metadata",
        () -> {
          assertThat(lastResponse).isNotNull();
          assertThat(lastResponse.path("total_elements").asInt()).isEqualTo(totalElements);
          assertThat(lastResponse.path("total_pages").asInt()).isEqualTo(totalPages);
          assertThat(lastResponse.path("number").asInt()).isZero();
          assertThat(lastResponse.path("size").asInt()).isEqualTo(20);
        });
  }

  @Then("the retention response contains the expected matching claims")
  public void retentionResponseContainsExpectedClaims() {
    step(
        "verify the retention response contains the expected claim identifiers and fields",
        () -> {
          List<Claim> orderedClaims =
              seededClaims.stream()
                  .sorted(
                      Comparator.comparing(Claim::getUpdatedOn, Comparator.reverseOrder())
                          .thenComparing(Claim::getId))
                  .toList();
          List<Claim> expectedClaims = orderedClaims;
          JsonNode content = lastResponse.path("content");

          assertThat(content).hasSize(expectedClaims.size());
          for (int index = 0; index < expectedClaims.size(); index++) {
            Claim expected = expectedClaims.get(index);
            JsonNode actual = content.get(index);

            assertThat(actual.path("claim_id").asText()).isEqualTo(expected.getId().toString());
            assertThat(actual.path("status").asText()).isEqualTo(ClaimStatus.VALID.name());
            assertThat(Instant.parse(actual.path("created_on").asText()))
                .isEqualTo(expected.getCreatedOn());
            assertThat(Instant.parse(actual.path("updated_on").asText()))
                .isEqualTo(expected.getUpdatedOn());
            assertThat(fieldNames(actual)).isEqualTo(CLAIM_FIELDS);
          }
        });
  }

  private Set<String> fieldNames(JsonNode node) {
    Set<String> names = new HashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
