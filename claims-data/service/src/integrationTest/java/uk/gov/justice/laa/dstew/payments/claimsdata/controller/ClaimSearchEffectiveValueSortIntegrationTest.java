package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getAssessmentBuilder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSetV2;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Verifies the {@code effective_total_value} sort key on {@code GET /api/v2/claims}: derivation of
 * the effective value across claim states, both sort directions, null placement for unpriced
 * claims, stable pagination via the {@code id} tie-breaker, composability with filters, and
 * rejection of an unsupported sort key.
 *
 * <p>Effective value precedence (single business rule): the allowed total (incl. VAT) of the latest
 * assessment when the claim has any assessment (0 for a void assessment); otherwise the latest
 * calculated fee total; otherwise {@code null}.
 */
@DisplayName("GET /api/v2/claims - sort by effective_total_value")
class ClaimSearchEffectiveValueSortIntegrationTest extends AbstractIntegrationTest {

  private static final String ENDPOINT = "/api/v2/claims";
  private static final String OFFICE = "EFVAL1";

  private Submission submission;

  @BeforeEach
  void seedOffice() {
    createBulkSubmission();
    submission =
        submissionRepository.save(
            Submission.builder()
                .id(Uuid7.timeBasedUuid())
                .bulkSubmissionId(bulkSubmission.getId())
                .officeAccountNumber(OFFICE)
                .submissionPeriod("JAN-2025")
                .areaOfLaw(AreaOfLaw.LEGAL_HELP)
                .status(SubmissionStatus.CREATED)
                .createdByUserId(USER_ID)
                .providerUserId(bulkSubmission.getCreatedByUserId())
                .numberOfClaims(0)
                .createdOn(CREATED_ON)
                .build());
  }

  @Test
  @DisplayName(
      "ascending orders lowest-to-highest by effective value; the displayed value matches the "
          + "ordering value, and an unpriced claim sorts last")
  void ascendingOrdersByEffectiveValueWithNullsLast() throws Exception {
    SeededClaims claims = seedEffectiveValueClaims();

    ClaimResultSetV2 result = search("effective_total_value,asc", 0, 20);

    assertThat(orderedIds(result))
        .as("ascending: void(0), assessed(150), untouched(300), repriced(500), unpriced(null last)")
        .containsExactly(
            claims.voided(),
            claims.assessed(),
            claims.untouched(),
            claims.repriced(),
            claims.unpriced());

    Map<UUID, BigDecimal> valuesById = displayedValuesById(result);
    assertThat(valuesById.get(claims.voided()))
        .as("voided claim shows £0.00 from its void assessment")
        .isEqualByComparingTo("0");
    assertThat(valuesById.get(claims.assessed()))
        .as("assessed claim shows the allowed total of its latest assessment")
        .isEqualByComparingTo("150");
    assertThat(valuesById.get(claims.untouched()))
        .as("untouched claim shows its latest calculated fee total")
        .isEqualByComparingTo("300");
    assertThat(valuesById.get(claims.repriced()))
        .as("repriced claim shows its latest (not earlier) calculated fee total")
        .isEqualByComparingTo("500");
    assertThat(valuesById.get(claims.unpriced()))
        .as("unpriced claim has no effective value")
        .isNull();
  }

  @Test
  @DisplayName("descending returns the exact reverse, placing the unpriced claim first")
  void descendingReversesOrderWithNullsFirst() throws Exception {
    SeededClaims claims = seedEffectiveValueClaims();

    ClaimResultSetV2 result = search("effective_total_value,desc", 0, 20);

    assertThat(orderedIds(result))
        .as("descending: unpriced(null first), repriced(500), untouched(300), assessed(150), void")
        .containsExactly(
            claims.unpriced(),
            claims.repriced(),
            claims.untouched(),
            claims.assessed(),
            claims.voided());
  }

  @Test
  @DisplayName("claims sharing an effective value page deterministically by id with no duplicates")
  void tiedValuesPageStablyById() throws Exception {
    // Two claims with an identical effective value of 200; created in a known order so the id
    // tie-breaker (ascending) yields a deterministic sequence across single-row pages.
    Claim first = newClaim(1, ClaimStatus.VALID);
    addCalculatedFee(first, newSummaryFee(first), "200", OffsetDateTime.now(ZoneOffset.UTC));
    Claim second = newClaim(2, ClaimStatus.VALID);
    addCalculatedFee(second, newSummaryFee(second), "200", OffsetDateTime.now(ZoneOffset.UTC));

    List<UUID> firstPage = orderedIds(search("effective_total_value,asc", 0, 1));
    List<UUID> secondPage = orderedIds(search("effective_total_value,asc", 1, 1));

    assertThat(firstPage).as("first page holds the lower id").containsExactly(first.getId());
    assertThat(secondPage).as("second page holds the higher id").containsExactly(second.getId());
    assertThat(firstPage)
        .as("no row is duplicated across pages")
        .doesNotContainAnyElementsOf(secondPage);
  }

  @Test
  @DisplayName(
      "distinct effective values order correctly across a page boundary, proving the sort is "
          + "applied to the whole result set before pagination")
  void distinctValuesOrderAcrossPageBoundary() throws Exception {
    // Three distinct values with a page size of 2 forces a boundary between two *different* values
    // (mid on page 0, high on page 1), so the value-based ORDER BY must span pages for this to
    // hold.
    Claim low = newClaim(1, ClaimStatus.VALID);
    addCalculatedFee(low, newSummaryFee(low), "100", OffsetDateTime.now(ZoneOffset.UTC));
    Claim mid = newClaim(2, ClaimStatus.VALID);
    addCalculatedFee(mid, newSummaryFee(mid), "200", OffsetDateTime.now(ZoneOffset.UTC));
    Claim high = newClaim(3, ClaimStatus.VALID);
    addCalculatedFee(high, newSummaryFee(high), "300", OffsetDateTime.now(ZoneOffset.UTC));

    List<UUID> firstPage = orderedIds(search("effective_total_value,asc", 0, 2));
    List<UUID> secondPage = orderedIds(search("effective_total_value,asc", 1, 2));

    assertThat(firstPage)
        .as("page 0 holds the two lowest values in ascending order")
        .containsExactly(low.getId(), mid.getId());
    assertThat(secondPage)
        .as("page 1 continues with the highest value")
        .containsExactly(high.getId());
  }

  @Test
  @DisplayName("the effective-value sort composes with a claim status filter")
  void effectiveValueSortComposesWithStatusFilter() throws Exception {
    Claim validHigh = newClaim(1, ClaimStatus.VALID);
    addCalculatedFee(
        validHigh, newSummaryFee(validHigh), "400", OffsetDateTime.now(ZoneOffset.UTC));
    Claim validLow = newClaim(2, ClaimStatus.VALID);
    addCalculatedFee(validLow, newSummaryFee(validLow), "100", OffsetDateTime.now(ZoneOffset.UTC));
    Claim invalid = newClaim(3, ClaimStatus.INVALID);
    addCalculatedFee(invalid, newSummaryFee(invalid), "50", OffsetDateTime.now(ZoneOffset.UTC));

    MvcResult mvcResult =
        mockMvc
            .perform(
                get(ENDPOINT)
                    .param("office_code", OFFICE)
                    .param("claim_statuses", ClaimStatus.VALID.name())
                    .param("sort", "effective_total_value,asc")
                    .param("page", "0")
                    .param("size", "20")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();
    ClaimResultSetV2 result =
        OBJECT_MAPPER.readValue(
            mvcResult.getResponse().getContentAsString(), ClaimResultSetV2.class);

    assertThat(orderedIds(result))
        .as("only VALID claims, ordered ascending by effective value; INVALID excluded")
        .containsExactly(validLow.getId(), validHigh.getId());
  }

  @Test
  @DisplayName(
      "a sort without an explicit page returns 200 with all rows ordered, not a 500 from the "
          + "unpaged pageable")
  void sortWithoutPageDoesNotError() throws Exception {
    Claim high = newClaim(1, ClaimStatus.VALID);
    addCalculatedFee(high, newSummaryFee(high), "300", OffsetDateTime.now(ZoneOffset.UTC));
    Claim low = newClaim(2, ClaimStatus.VALID);
    addCalculatedFee(low, newSummaryFee(low), "100", OffsetDateTime.now(ZoneOffset.UTC));

    MvcResult mvcResult =
        mockMvc
            .perform(
                get(ENDPOINT)
                    .param("office_code", OFFICE)
                    .param("sort", "effective_total_value,asc")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();
    ClaimResultSetV2 result =
        OBJECT_MAPPER.readValue(
            mvcResult.getResponse().getContentAsString(), ClaimResultSetV2.class);

    assertThat(orderedIds(result))
        .as("all rows returned in ascending effective-value order despite no page parameter")
        .containsExactly(low.getId(), high.getId());
  }

  @Test
  @DisplayName("an unsupported sort key is rejected with 400, consistent with other sort fields")
  void unsupportedSortKeyIsRejected() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("office_code", OFFICE)
                .param("sort", "not_a_real_field,asc")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest());
  }

  // ---------------------------------------------------------------------------
  // Fixture helpers
  // ---------------------------------------------------------------------------

  private record SeededClaims(
      UUID voided, UUID assessed, UUID untouched, UUID repriced, UUID unpriced) {}

  /**
   * Seeds one claim per state permutation under the isolated office. Claims are created in
   * ascending-effective-value order so the returned ids are also monotonically increasing, which
   * keeps the tie-breaker orthogonal to the assertions here.
   */
  private SeededClaims seedEffectiveValueClaims() {
    // Voided: void assessment records a zero allowed total, so effective value resolves to 0.
    Claim voided = newClaim(1, ClaimStatus.VOID);
    ClaimSummaryFee voidedFee = newSummaryFee(voided);
    addCalculatedFee(voided, voidedFee, "999", OffsetDateTime.now(ZoneOffset.UTC));
    addAssessment(voided, voidedFee, AssessmentType.VOID, "0");

    // Assessed: the latest assessment's allowed total wins over any calculated fee.
    Claim assessed = newClaim(2, ClaimStatus.VALID);
    ClaimSummaryFee assessedFee = newSummaryFee(assessed);
    addCalculatedFee(assessed, assessedFee, "999", OffsetDateTime.now(ZoneOffset.UTC));
    addAssessment(assessed, assessedFee, AssessmentType.ESCAPE_CASE_ASSESSMENT, "150");

    // Untouched: a single calculated fee, no assessment.
    Claim untouched = newClaim(3, ClaimStatus.VALID);
    addCalculatedFee(
        untouched, newSummaryFee(untouched), "300", OffsetDateTime.now(ZoneOffset.UTC));

    // Repriced: two calculated fees; the latest (by created_on) is the effective value.
    Claim repriced = newClaim(4, ClaimStatus.VALID);
    ClaimSummaryFee repricedFee = newSummaryFee(repriced);
    addCalculatedFee(repriced, repricedFee, "100", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
    addCalculatedFee(repriced, repricedFee, "500", OffsetDateTime.now(ZoneOffset.UTC));

    // Unpriced: no calculated fee and no assessment, so no effective value.
    Claim unpriced = newClaim(5, ClaimStatus.VALID);

    return new SeededClaims(
        voided.getId(), assessed.getId(), untouched.getId(), repriced.getId(), unpriced.getId());
  }

  private Claim newClaim(int lineNumber, ClaimStatus status) {
    Claim claim = new Claim();
    claim.setId(Uuid7.timeBasedUuid());
    claim.setSubmission(submission);
    claim.setCreatedByUserId(API_USER_ID);
    claim.setMatterTypeCode("TEST-MTC");
    claim.setLineNumber(lineNumber);
    claim.setStatus(status);
    return claimRepository.saveAndFlush(claim);
  }

  private ClaimSummaryFee newSummaryFee(Claim claim) {
    return claimSummaryFeeRepository.saveAndFlush(
        ClaimSummaryFee.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claim)
            .createdByUserId("Test")
            .build());
  }

  private void addCalculatedFee(
      Claim claim, ClaimSummaryFee fee, String totalAmount, OffsetDateTime createdOn) {
    CalculatedFeeDetail cfd = new CalculatedFeeDetail();
    cfd.setId(Uuid7.timeBasedUuid());
    cfd.setClaim(claim);
    cfd.setClaimSummaryFee(fee);
    cfd.setFeeCode("FEE-123");
    cfd.setTotalAmount(new BigDecimal(totalAmount));
    cfd.setCreatedByUserId("Test");
    cfd.setCreatedOn(createdOn);
    calculatedFeeDetailRepository.saveAndFlush(cfd);
  }

  private void addAssessment(
      Claim claim, ClaimSummaryFee fee, AssessmentType type, String allowedTotalInclVat) {
    Assessment assessment =
        getAssessmentBuilder()
            .id(Uuid7.timeBasedUuid())
            .claim(claim)
            .claimSummaryFee(fee)
            .assessmentType(type)
            .assessmentReason("test")
            .allowedTotalInclVat(new BigDecimal(allowedTotalInclVat))
            .build();
    assessmentRepository.saveAndFlush(assessment);
  }

  private ClaimResultSetV2 search(String sort, int page, int size) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get(ENDPOINT)
                    .param("office_code", OFFICE)
                    .param("sort", sort)
                    .param("page", String.valueOf(page))
                    .param("size", String.valueOf(size))
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();
    return OBJECT_MAPPER.readValue(
        result.getResponse().getContentAsString(), ClaimResultSetV2.class);
  }

  private static List<UUID> orderedIds(ClaimResultSetV2 result) {
    return result.getContent().stream().map(claim -> UUID.fromString(claim.getId())).toList();
  }

  private static Map<UUID, BigDecimal> displayedValuesById(ClaimResultSetV2 result) {
    // A HashMap is used deliberately: an unpriced claim exposes a null effective value, which
    // Collectors.toMap rejects.
    Map<UUID, BigDecimal> valuesById = new HashMap<>();
    result
        .getContent()
        .forEach(
            claim ->
                valuesById.put(UUID.fromString(claim.getId()), claim.getEffectiveTotalValue()));
    return valuesById;
  }
}
