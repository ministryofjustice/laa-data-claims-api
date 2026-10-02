package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
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

/**
 * Step glue for {@code claimsSearchDerivedStatus.feature} — DSTEW-1948.
 *
 * <p>Drives the real {@code GET /api/v2/claims} search over HTTP and asserts the shipped {@code
 * derived_claim_status} field and its sort key. Claims are seeded directly through JPA (mirroring
 * the integration suite's {@code DerivedClaimStatusSortTests}) with the raw {@code claim_status}
 * and the {@code has_assessment} / {@code is_amended} flags that the derivation reads — no new data
 * capture, exactly as the story requires.
 *
 * <p>{@link uk.gov.justice.laa.dstew.payments.claimsdata.bdd.hooks.BddHooks} truncates every table
 * before each scenario, so a single fixed office code per run is isolation-safe; all searches are
 * office-scoped to it.
 */
public class ClaimsSearchDerivedStatusSteps {

  private static final String SEED_ACTOR = "bdd-DSTEW-1948";
  private static final String OFFICE = "DS1948-OFC";
  private static final String MATTER_TYPE_CODE = "TEST-MTC";
  private static final String V2_CLAIMS = "/api/v2/claims";
  private static final String V1_CLAIMS = "/api/v1/claims";

  private static final Map<String, Integer> ORDINALS =
      Map.of("1st", 0, "2nd", 1, "3rd", 2, "4th", 3);

  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private RestTemplate restTemplate;
  @Autowired private BddServerInfo serverInfo;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AtomicInteger lineSeq = new AtomicInteger();
  private final List<UUID> seededClaimIds = new ArrayList<>();
  private final Map<Integer, List<String>> pageResults = new LinkedHashMap<>();

  private Submission submission;
  private UUID singleClaimId;
  private int pageSize;
  private int lastPageRequested;
  private JsonNode lastResponse;
  private int lastStatus;

  // ---------------------------------------------------------------------------
  // Background.
  // ---------------------------------------------------------------------------

  @Given("the v2 claims search endpoint is available")
  public void theV2ClaimsSearchEndpointIsAvailable() {
    step(
        "confirm the running application base URL is resolvable for the search calls",
        () -> assertThat(serverInfo.baseUrl()).isNotBlank());
  }

  // ---------------------------------------------------------------------------
  // Givens — seeding.
  // ---------------------------------------------------------------------------

  @Given("a claim exists with claim_status {string}, has_assessment {word} and is_amended {word}")
  public void aClaimExistsWith(String claimStatus, String hasAssessment, String isAmended) {
    step(
        "seed one claim with the given raw status and derivation flags",
        () ->
            singleClaimId =
                persistClaim(
                        ClaimStatus.fromValue(claimStatus),
                        Boolean.parseBoolean(hasAssessment),
                        Boolean.parseBoolean(isAmended))
                    .getId());
  }

  @Given("a VALID claim that has been both amended and assessed")
  public void aValidClaimAmendedAndAssessed() {
    step(
        "seed a VALID claim with both is_amended and has_assessment true",
        () -> singleClaimId = persistClaim(ClaimStatus.VALID, true, true).getId());
  }

  @Given("a VALID claim that has been assessed")
  public void aValidClaimAssessed() {
    step(
        "seed a VALID claim with has_assessment true",
        () -> singleClaimId = persistClaim(ClaimStatus.VALID, true, false).getId());
  }

  @Given("a VALID claim that has been amended")
  public void aValidClaimAmended() {
    step(
        "seed a VALID claim with is_amended true",
        () -> singleClaimId = persistClaim(ClaimStatus.VALID, false, true).getId());
  }

  @Given("the office has one claim in each derived status")
  public void oneClaimInEachDerivedStatus() {
    step(
        "seed exactly one claim per derived status under the office",
        () -> {
          persistClaim(ClaimStatus.VALID, false, false); // ACCEPTED
          persistClaim(ClaimStatus.VALID, false, true); // AMENDED
          persistClaim(ClaimStatus.VALID, true, false); // ASSESSED
          persistClaim(ClaimStatus.VOID, false, false); // VOIDED
          persistClaim(ClaimStatus.INVALID, false, false); // INVALID
          persistClaim(ClaimStatus.READY_TO_PROCESS, false, false); // READY_TO_PROCESS
          persistClaim(ClaimStatus.VALIDATED_PENDING_APPROVAL, false, false); // VPA
        });
  }

  @Given("the office has 4 ACCEPTED claims seeded in order")
  public void fourAcceptedClaims() {
    step(
        "seed four ACCEPTED (VALID, no assessment, not amended) claims in insertion order",
        () -> {
          for (int i = 0; i < 4; i++) {
            persistClaim(ClaimStatus.VALID, false, false);
          }
        });
  }

  @Given("the office has ACCEPTED, AMENDED, ASSESSED, VOIDED and INVALID claims")
  public void mixedAcrossStatuses() {
    step(
        "seed one claim for each of five derived statuses",
        () -> {
          persistClaim(ClaimStatus.VALID, false, false); // ACCEPTED
          persistClaim(ClaimStatus.VALID, false, true); // AMENDED
          persistClaim(ClaimStatus.VALID, true, false); // ASSESSED
          persistClaim(ClaimStatus.VOID, false, false); // VOIDED
          persistClaim(ClaimStatus.INVALID, false, false); // INVALID
        });
  }

  @Given("the office has one ACCEPTED claim")
  public void oneAcceptedClaim() {
    step("seed a single ACCEPTED claim", () -> persistClaim(ClaimStatus.VALID, false, false));
  }

  @And("the page size is {int}")
  public void thePageSizeIs(int size) {
    step("record the requested page size", () -> pageSize = size);
  }

  // ---------------------------------------------------------------------------
  // Whens — searching.
  // ---------------------------------------------------------------------------

  @When("I search claims")
  public void iSearchClaims() {
    step("GET /api/v2/claims for the office", () -> searchV2(params("size", "50")));
  }

  @When("I search claims on the v2 endpoint")
  public void iSearchClaimsV2() {
    step("GET /api/v2/claims for the office", () -> searchV2(params("size", "50")));
  }

  @When("I search claims on the v1 endpoint")
  public void iSearchClaimsV1() {
    step("GET /api/v1/claims for the office", () -> search(V1_CLAIMS, params("size", "50")));
  }

  @When("I search claims sorted by {string} ascending")
  public void iSearchSortedAscending(String sortKey) {
    step(
        "GET /api/v2/claims sorted ascending by " + sortKey,
        () -> searchV2(params("size", "50", "sort", sortKey + ",asc")));
  }

  @When("I search claims sorted by {string} descending")
  public void iSearchSortedDescending(String sortKey) {
    step(
        "GET /api/v2/claims sorted descending by " + sortKey,
        () -> searchV2(params("size", "50", "sort", sortKey + ",desc")));
  }

  @When("I search claims filtered by claim_statuses {string} sorted by {string} ascending")
  public void iSearchFilteredSortedAscending(String claimStatuses, String sortKey) {
    step(
        "GET /api/v2/claims filtered by claim_statuses + sorted ascending",
        () ->
            searchV2(
                params("size", "50", "claim_statuses", claimStatuses, "sort", sortKey + ",asc")));
  }

  @When("I request page {int} sorted by {string} ascending")
  public void iRequestPageSortedAscending(int oneBasedPage, String sortKey) {
    step(
        "GET /api/v2/claims page " + oneBasedPage + " sorted ascending by " + sortKey,
        () -> {
          lastPageRequested = oneBasedPage;
          searchV2(
              params(
                  "size",
                  String.valueOf(pageSize),
                  "page",
                  String.valueOf(oneBasedPage - 1),
                  "sort",
                  sortKey + ",asc"));
          pageResults.put(oneBasedPage, contentIds());
        });
  }

  // ---------------------------------------------------------------------------
  // Thens.
  // ---------------------------------------------------------------------------

  @Then("that claim's derived_claim_status is {string}")
  public void thatClaimsDerivedStatusIs(String expected) {
    step(
        "assert the single seeded claim derives " + expected,
        () -> assertThat(derivedStatusOf(singleClaimId)).isEqualTo(expected));
  }

  @Then("that claim's raw status field is {string}")
  public void thatClaimsRawStatusIs(String expected) {
    step(
        "assert the raw status field is unchanged (" + expected + ")",
        () -> assertThat(fieldOf(singleClaimId, "status")).isEqualTo(expected));
  }

  @Then("the v2 result for that claim includes derived_claim_status {string}")
  public void v2ResultIncludesDerivedStatus(String expected) {
    step(
        "assert the v2 response carries derived_claim_status=" + expected,
        () -> assertThat(derivedStatusOf(singleClaimId)).isEqualTo(expected));
  }

  @Then("no claim in the v1 result carries a derived_claim_status field")
  public void v1ResultHasNoDerivedStatus() {
    step(
        "assert the v1 response omits derived_claim_status on every row",
        () -> {
          JsonNode content = lastResponse.path("content");
          assertThat(content.isArray() && !content.isEmpty())
              .as("v1 search returned the seeded claim(s)")
              .isTrue();
          for (JsonNode row : content) {
            assertThat(row.has("derived_claim_status"))
                .as("v1 row %s must not carry derived_claim_status", row.path("id").asText())
                .isFalse();
          }
        });
  }

  @Then("^the derived statuses are ordered: (.+)$")
  public void theDerivedStatusesAreOrdered(String csv) {
    step(
        "assert the derived-status sequence equals " + csv,
        () -> assertThat(derivedStatusesInOrder()).containsExactlyElementsOf(split(csv)));
  }

  @Then("^only the VALID-derived claims are returned, ordered: (.+)$")
  public void onlyValidDerivedClaimsReturned(String csv) {
    step(
        "assert the filter kept only VALID-derived claims in order " + csv,
        () -> assertThat(derivedStatusesInOrder()).containsExactlyElementsOf(split(csv)));
  }

  @Then("the results are the {word} and {word} seeded claims in id order")
  public void theResultsAreTheSeededClaims(String first, String second) {
    step(
        "assert the page returns the expected id-ordered seeded claims",
        () -> {
          List<String> idOrder = seededIdsAscending();
          List<String> expected =
              List.of(idOrder.get(ORDINALS.get(first)), idOrder.get(ORDINALS.get(second)));
          assertThat(pageResults.get(lastPageRequested)).containsExactlyElementsOf(expected);
        });
  }

  @And("no claim is duplicated or dropped across the page boundary")
  public void noClaimDuplicatedOrDropped() {
    step(
        "assert the two pages together cover every seeded claim exactly once",
        () -> {
          List<String> combined = new ArrayList<>();
          pageResults.values().forEach(combined::addAll);
          assertThat(combined).doesNotHaveDuplicates();
          assertThat(combined).containsExactlyInAnyOrderElementsOf(seededIdsAscending());
        });
  }

  @Then("the search response status is {int}")
  public void theSearchResponseStatusIs(int expected) {
    step(
        "assert the last search returned HTTP " + expected,
        () -> assertThat(lastStatus).isEqualTo(expected));
  }

  @And("searching sorted by {string} ascending is accepted with status {int}")
  public void searchingSortedIsAccepted(String sortKey, int expected) {
    step(
        "assert " + sortKey + " is a recognised sort key (HTTP " + expected + ")",
        () -> {
          searchV2(params("size", "50", "sort", sortKey + ",asc"));
          assertThat(lastStatus).isEqualTo(expected);
        });
  }

  // ---------------------------------------------------------------------------
  // Helpers.
  // ---------------------------------------------------------------------------

  private Submission ensureSubmission() {
    if (submission == null) {
      submission =
          submissionRepository.saveAndFlush(
              Submission.builder()
                  .id(Uuid7.timeBasedUuid())
                  .officeAccountNumber(OFFICE)
                  .submissionPeriod("FEB-2025")
                  .areaOfLaw(AreaOfLaw.CRIME_LOWER)
                  .status(SubmissionStatus.CREATED)
                  .providerUserId(SEED_ACTOR)
                  .createdByUserId(SEED_ACTOR)
                  .createdOn(Instant.now())
                  .build());
    }
    return submission;
  }

  private Claim persistClaim(ClaimStatus status, boolean hasAssessment, boolean isAmended) {
    Claim claim =
        claimRepository.saveAndFlush(
            Claim.builder()
                .id(Uuid7.timeBasedUuid())
                .submission(ensureSubmission())
                .status(status)
                .hasAssessment(hasAssessment)
                .isAmended(isAmended)
                .lineNumber(lineSeq.incrementAndGet())
                .matterTypeCode(MATTER_TYPE_CODE)
                .createdByUserId(SEED_ACTOR)
                .build());
    seededClaimIds.add(claim.getId());
    return claim;
  }

  private Map<String, String> params(String... keyValues) {
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i + 1 < keyValues.length; i += 2) {
      map.put(keyValues[i], keyValues[i + 1]);
    }
    return map;
  }

  private void searchV2(Map<String, String> params) {
    search(V2_CLAIMS, params);
  }

  private void search(String path, Map<String, String> params) {
    StringBuilder url =
        new StringBuilder(serverInfo.baseUrl()).append(path).append("?office_code=").append(OFFICE);
    params.forEach((k, v) -> url.append('&').append(k).append('=').append(v));

    HttpHeaders headers = new HttpHeaders();
    headers.add(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN);
    HttpEntity<Void> request = new HttpEntity<>(headers);

    try {
      ResponseEntity<String> response =
          restTemplate.exchange(url.toString(), HttpMethod.GET, request, String.class);
      lastStatus = response.getStatusCode().value();
      lastResponse = parse(response.getBody());
    } catch (HttpStatusCodeException ex) {
      lastStatus = ex.getStatusCode().value();
      lastResponse = parse(ex.getResponseBodyAsString());
    }
  }

  private JsonNode parse(String body) {
    try {
      return (body == null || body.isBlank())
          ? objectMapper.nullNode()
          : objectMapper.readTree(body);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to parse search response body as JSON", ex);
    }
  }

  private JsonNode rowFor(UUID claimId) {
    for (JsonNode row : lastResponse.path("content")) {
      if (claimId.toString().equals(row.path("id").asText())) {
        return row;
      }
    }
    throw new AssertionError("Claim " + claimId + " not present in the search response content");
  }

  private String derivedStatusOf(UUID claimId) {
    return rowFor(claimId).path("derived_claim_status").asText();
  }

  private String fieldOf(UUID claimId, String field) {
    return rowFor(claimId).path(field).asText();
  }

  private List<String> derivedStatusesInOrder() {
    List<String> out = new ArrayList<>();
    for (JsonNode row : lastResponse.path("content")) {
      out.add(row.path("derived_claim_status").asText());
    }
    return out;
  }

  private List<String> contentIds() {
    List<String> out = new ArrayList<>();
    for (JsonNode row : lastResponse.path("content")) {
      out.add(row.path("id").asText());
    }
    return out;
  }

  private List<String> seededIdsAscending() {
    return seededClaimIds.stream().map(UUID::toString).sorted().toList();
  }

  private static List<String> split(String csv) {
    return Arrays.stream(csv.split(",")).map(String::trim).toList();
  }
}
