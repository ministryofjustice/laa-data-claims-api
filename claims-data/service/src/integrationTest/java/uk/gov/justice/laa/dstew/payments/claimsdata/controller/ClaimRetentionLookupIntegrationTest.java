package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.OFFICE_ACCOUNT_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.UNIQUE_FILE_NUMBER;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimRetentionLookupClaimDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimRetentionLookupResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/** Integration tests for retention lookup endpoint. */
@Transactional
@DisplayName("Claim Retention Lookup Integration Test")
public class ClaimRetentionLookupIntegrationTest extends AbstractIntegrationTest {

  private static final String RETENTION_LOOKUP_ENDPOINT =
      API_URI_PREFIX + "/claims/retention-lookup";

  @Autowired private MockMvc mockMvc;
  @Autowired private ClaimRepository claimRepository;

  @Autowired private SubmissionRepository submissionRepository;

  private Submission testSubmission;

  /**
   * Running line number allocator. Claims are unique per (submission_id, line_number), so tests
   * that seed several batches against the same submission must never restart at 1.
   */
  private int nextLineNumber;

  /** Makes each simulated amendment write a distinct value so the entity is always dirty. */
  private int amendmentCount;

  @BeforeEach
  public void setUp() {
    nextLineNumber = 1;
    amendmentCount = 0;
    // Create a test submission with all required fields
    testSubmission =
        Submission.builder()
            .id(Uuid7.timeBasedUuid())
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
            .submissionPeriod("JAN-2025")
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.VALIDATION_SUCCEEDED)
            .createdByUserId("test-user")
            .providerUserId("test-provider")
            .createdOn(Instant.now())
            .build();
    submissionRepository.saveAndFlush(testSubmission);
  }

  // Pagination test cases

  @Test
  @DisplayName("Should return paginated VALID claims with default pagination")
  void testDefaultPagination() throws Exception {
    // Create 50 VALID claims for the same office+ufn
    createTestClaims(50, ClaimStatus.VALID);

    MvcResult result =
        mockMvc
            .perform(
                get(RETENTION_LOOKUP_ENDPOINT)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER)
                    .param("ufn", UNIQUE_FILE_NUMBER)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total_elements").value(50))
            .andExpect(jsonPath("$.total_pages").value(3))
            .andExpect(jsonPath("$.number").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.content.length()").value(20))
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    ClaimRetentionLookupResultSet response =
        OBJECT_MAPPER.readValue(responseBody, ClaimRetentionLookupResultSet.class);

    assertThat(response.getContent()).hasSize(20);
    assertThat(response.getTotalElements()).isEqualTo(50);
    assertThat(response.getTotalPages()).isEqualTo(3);
  }

  @Test
  @DisplayName("Should return second page of VALID claims")
  void testSecondPage() throws Exception {
    createTestClaims(50, ClaimStatus.VALID);

    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", OFFICE_ACCOUNT_NUMBER)
                .param("ufn", UNIQUE_FILE_NUMBER)
                .param("page", "1")
                .param("size", "20")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(50))
        .andExpect(jsonPath("$.number").value(1))
        .andExpect(jsonPath("$.content.length()").value(20))
        .andReturn();
  }

  @Test
  @DisplayName("Should return custom page size")
  void testCustomPageSize() throws Exception {
    createTestClaims(50, ClaimStatus.VALID);

    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", OFFICE_ACCOUNT_NUMBER)
                .param("ufn", UNIQUE_FILE_NUMBER)
                .param("page", "0")
                .param("size", "10")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(50))
        .andExpect(jsonPath("$.total_pages").value(5))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.content.length()").value(10))
        .andReturn();
  }

  @Test
  @DisplayName("Should return last page with partial results")
  void testLastPagePartial() throws Exception {
    createTestClaims(25, ClaimStatus.VALID);

    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", OFFICE_ACCOUNT_NUMBER)
                .param("ufn", UNIQUE_FILE_NUMBER)
                .param("page", "1")
                .param("size", "20")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(25))
        .andExpect(jsonPath("$.total_pages").value(2))
        .andExpect(jsonPath("$.number").value(1))
        .andExpect(jsonPath("$.content.length()").value(5))
        .andReturn();
  }

  // Business rules and filtering test cases
  @Test
  @DisplayName("Should return empty content array when no match")
  void testNoMatchReturnsEmptyArray() throws Exception {
    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", "ZZZ999")
                .param("ufn", "311224/999")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.total_elements").value(0))
        .andExpect(jsonPath("$.total_pages").value(0))
        .andReturn();
  }

  @ParameterizedTest
  @EnumSource(
      value = ClaimStatus.class,
      names = {"INVALID", "VOID", "READY_TO_PROCESS", "VALIDATED_PENDING_APPROVAL"})
  @DisplayName("Should filter out every non-VALID retention status")
  void testFiltersOutNonValidClaims(ClaimStatus excludedStatus) throws Exception {
    createTestClaims(5, ClaimStatus.VALID);
    createTestClaims(3, excludedStatus);

    MvcResult result =
        mockMvc
            .perform(
                get(RETENTION_LOOKUP_ENDPOINT)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER)
                    .param("ufn", UNIQUE_FILE_NUMBER)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total_elements").value(5))
            .andReturn();

    ClaimRetentionLookupResultSet response =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(), ClaimRetentionLookupResultSet.class);
    assertThat(response.getContent())
        .allMatch(
            claim -> ClaimRetentionLookupClaimDetail.StatusEnum.VALID.equals(claim.getStatus()));
  }

  @Test
  @DisplayName("Should sort by updated_on DESC")
  void testSortByUpdatedOn() throws Exception {
    saveClaim(ClaimStatus.VALID);
    Claim amendedLast = saveClaim(ClaimStatus.VALID);
    saveClaim(ClaimStatus.VALID);
    // Amending the middle claim makes it the most recently updated of the three.
    amendClaim(amendedLast);

    MvcResult result =
        mockMvc
            .perform(
                get(RETENTION_LOOKUP_ENDPOINT)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER)
                    .param("ufn", UNIQUE_FILE_NUMBER)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

    ClaimRetentionLookupResultSet response =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(), ClaimRetentionLookupResultSet.class);

    assertThat(response.getContent()).hasSize(3);
    assertThat(response.getContent().get(0).getClaimId()).isEqualTo(amendedLast.getId());
    assertThat(response.getContent())
        .extracting(detail -> detail.getUpdatedOn().toInstant())
        .isSortedAccordingTo(Comparator.reverseOrder());
  }

  @ParameterizedTest
  @ValueSource(strings = {"createdOn,asc", "notAClaimProperty,asc", "client.clientSurname,asc"})
  @DisplayName("Should ignore caller sorting and retain the fixed retention order")
  void testCallerSortIsIgnored(String sort) throws Exception {
    saveClaim(ClaimStatus.VALID);
    Claim amendedLast = saveClaim(ClaimStatus.VALID);
    saveClaim(ClaimStatus.VALID);
    amendClaim(amendedLast);

    MvcResult result =
        mockMvc
            .perform(
                get(RETENTION_LOOKUP_ENDPOINT)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER)
                    .param("ufn", UNIQUE_FILE_NUMBER)
                    .param("page", "0")
                    .param("size", "20")
                    .param("sort", sort)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

    ClaimRetentionLookupResultSet response =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(), ClaimRetentionLookupResultSet.class);

    assertThat(response.getContent()).hasSize(3);
    assertThat(response.getContent().get(0).getClaimId()).isEqualTo(amendedLast.getId());
    assertThat(response.getContent())
        .extracting(detail -> detail.getUpdatedOn().toInstant())
        .isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  @DisplayName("Should clamp a negative page to the first page rather than failing")
  void testNegativePageParam() throws Exception {
    createTestClaims(5, ClaimStatus.VALID);

    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", OFFICE_ACCOUNT_NUMBER)
                .param("ufn", UNIQUE_FILE_NUMBER)
                .param("page", "-1")
                .param("size", "20")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.number").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.total_elements").value(5))
        .andReturn();
  }

  @Test
  @DisplayName("Should require exact office_code match (case-sensitive composite key validation)")
  void testOfficeCodeExactMatch() throws Exception {
    createTestClaims(1, ClaimStatus.VALID);

    // Test exact match works
    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", OFFICE_ACCOUNT_NUMBER)
                .param("ufn", UNIQUE_FILE_NUMBER)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andReturn();

    // Test different office code returns empty
    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", "ZZZ999")
                .param("ufn", UNIQUE_FILE_NUMBER)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andReturn();
  }

  @Test
  @DisplayName("Should require exact UFN match (case-sensitive composite key validation)")
  void testUfnExactMatch() throws Exception {
    createTestClaims(1, ClaimStatus.VALID);

    // Test exact match works
    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", OFFICE_ACCOUNT_NUMBER)
                .param("ufn", UNIQUE_FILE_NUMBER)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andReturn();

    // Test different UFN returns empty
    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", OFFICE_ACCOUNT_NUMBER)
                .param("ufn", "311224/999")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andReturn();
  }

  @Test
  @DisplayName("Should require both office_code AND ufn to match (composite key)")
  void testCompositeKeyRequiresBoth() throws Exception {
    // Create claims with known office+ufn
    createTestClaims(1, ClaimStatus.VALID);

    // Only correct office but wrong UFN
    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", OFFICE_ACCOUNT_NUMBER)
                .param("ufn", "311224/999")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andReturn();

    // Only correct UFN but wrong office
    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", "ZZZ999")
                .param("ufn", UNIQUE_FILE_NUMBER)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andReturn();
  }

  @Test
  @DisplayName("Should return 400 when office_code is omitted")
  void testMissingOfficeCodeReturnsBadRequest() throws Exception {
    createTestClaims(3, ClaimStatus.VALID);

    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("ufn", UNIQUE_FILE_NUMBER)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Required parameter 'office_code' is not present."))
        .andReturn();
  }

  @Test
  @DisplayName("Should return 400 when ufn is omitted")
  void testMissingUfnReturnsBadRequest() throws Exception {
    createTestClaims(3, ClaimStatus.VALID);

    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", OFFICE_ACCOUNT_NUMBER)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Required parameter 'ufn' is not present."))
        .andReturn();
  }

  @Test
  @DisplayName("Should return 400 when both office_code and ufn are omitted")
  void testMissingBothIdentifiersReturnsBadRequest() throws Exception {
    createTestClaims(3, ClaimStatus.VALID);

    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andReturn();
  }

  @Test
  @DisplayName("Should return 400 when office_code is blank")
  void testBlankOfficeCodeRejected() throws Exception {
    createTestClaims(3, ClaimStatus.VALID);

    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", "")
                .param("ufn", UNIQUE_FILE_NUMBER)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andReturn();
  }

  @Test
  @DisplayName("Should return 400 when ufn is blank")
  void testBlankUfnRejected() throws Exception {
    createTestClaims(3, ClaimStatus.VALID);

    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", OFFICE_ACCOUNT_NUMBER)
                .param("ufn", "")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andReturn();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "SHORT", // 5 characters
        "TOOLONG7", // 8 characters
        "OFF-12", // contains a hyphen
        "OFF 12" // contains a space
      })
  @DisplayName("Should return 400 when office_code is not 6 alphanumeric characters")
  void testMalformedOfficeCodeRejected(String officeCode) throws Exception {
    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", officeCode)
                .param("ufn", UNIQUE_FILE_NUMBER)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andReturn();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "23041993/001", // DDMMYYYY - the date part must be 6 digits, not 8
        "010125/01", // sequence must be 3 digits
        "010125-001", // wrong separator
        "01012/001", // date part too short
        "ABCDEF/001" // date part must be numeric
      })
  @DisplayName("Should return 400 when ufn is not in DDMMYY/NNN format")
  void testMalformedUfnRejected(String ufn) throws Exception {
    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", OFFICE_ACCOUNT_NUMBER)
                .param("ufn", ufn)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andReturn();
  }

  @Test
  @DisplayName("Should accept a well-formed office_code and ufn that match no claim")
  void testWellFormedIdentifiersWithNoMatchReturnEmptyPage() throws Exception {
    createTestClaims(3, ClaimStatus.VALID);

    // Format is valid, so this is a genuine "no qualifying claim" answer rather than a
    // rejected request - the distinction RCW needs for retention decisions.
    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", "9ZZ999")
                .param("ufn", "010125/999")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.total_elements").value(0))
        .andReturn();
  }

  @Test
  @DisplayName("Should return 401 when no authorization token is supplied")
  void testMissingAuthTokenReturnsUnauthorized() throws Exception {
    createTestClaims(3, ClaimStatus.VALID);

    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", OFFICE_ACCOUNT_NUMBER)
                .param("ufn", UNIQUE_FILE_NUMBER)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andReturn();
  }

  @Test
  @DisplayName("Should return 401 when an invalid authorization token is supplied")
  void testInvalidAuthTokenReturnsUnauthorized() throws Exception {
    createTestClaims(3, ClaimStatus.VALID);

    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", OFFICE_ACCOUNT_NUMBER)
                .param("ufn", UNIQUE_FILE_NUMBER)
                .header(AUTHORIZATION_HEADER, INVALID_AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andReturn();
  }

  @Test
  @DisplayName("Should return 401 before validating identifiers when token is missing")
  void testMissingAuthTokenTakesPrecedenceOverInvalidIdentifiers() throws Exception {
    // office_code is omitted, which would otherwise be a 400; authentication must fail first
    // so an unauthenticated caller cannot probe the endpoint's validation behaviour.
    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("ufn", UNIQUE_FILE_NUMBER)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andReturn();
  }

  @Test
  @DisplayName(
      "Should return VALID claims only (acceptance marker for retention three-year period)")
  void testOnlyValidClaimsReturned() throws Exception {
    createTestClaims(5, ClaimStatus.VALID);
    createTestClaims(3, ClaimStatus.INVALID);
    createTestClaims(2, ClaimStatus.READY_TO_PROCESS);

    MvcResult result =
        mockMvc
            .perform(
                get(RETENTION_LOOKUP_ENDPOINT)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER)
                    .param("ufn", UNIQUE_FILE_NUMBER)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total_elements").value(5))
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    ClaimRetentionLookupResultSet response =
        OBJECT_MAPPER.readValue(responseBody, ClaimRetentionLookupResultSet.class);

    // Verify all returned claims have VALID status
    assertThat(response.getContent())
        .allMatch(
            claim -> ClaimRetentionLookupClaimDetail.StatusEnum.VALID.equals(claim.getStatus()),
            "All claims should have VALID status");
  }

  @Test
  @DisplayName("Should use updatedOn to capture most recent amendment (extends three-year period)")
  void testUpdatedOnCapturesAmendments() throws Exception {
    Claim claim = saveClaim(ClaimStatus.VALID);
    Instant beforeAmendment = claim.getUpdatedOn();

    Claim amended = amendClaim(claim);

    MvcResult result =
        mockMvc
            .perform(
                get(RETENTION_LOOKUP_ENDPOINT)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER)
                    .param("ufn", UNIQUE_FILE_NUMBER)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total_elements").value(1))
            .andReturn();

    ClaimRetentionLookupResultSet response =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(), ClaimRetentionLookupResultSet.class);

    // The amendment must move updatedOn forward, extending the retention window, while
    // createdOn (the start of the three-year period) stays anchored to the initial claim.
    assertThat(response.getContent().get(0).getUpdatedOn().toInstant())
        .isEqualTo(amended.getUpdatedOn())
        .isAfter(beforeAmendment);
    assertThat(response.getContent().get(0).getCreatedOn().toInstant())
        .isEqualTo(claim.getCreatedOn());
  }

  @Test
  @DisplayName("Should return most recent claim date when multiple amendments exist")
  void testMostRecentAmendmentDateReturned() throws Exception {
    Claim firstClaim = saveClaim(ClaimStatus.VALID);
    Claim secondClaim = saveClaim(ClaimStatus.VALID);

    // Both claims are amended; the first one is amended last so it carries the most
    // recent retention-relevant date for this office+ufn composite key.
    amendClaim(secondClaim);
    Claim mostRecentlyAmended = amendClaim(firstClaim);

    MvcResult result =
        mockMvc
            .perform(
                get(RETENTION_LOOKUP_ENDPOINT)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER)
                    .param("ufn", UNIQUE_FILE_NUMBER)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total_elements").value(2))
            .andReturn();

    ClaimRetentionLookupResultSet response =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(), ClaimRetentionLookupResultSet.class);

    // First result drives the retention decision: the most recent amendment.
    assertThat(response.getContent().get(0).getClaimId()).isEqualTo(mostRecentlyAmended.getId());
    assertThat(response.getContent().get(0).getUpdatedOn().toInstant())
        .isEqualTo(mostRecentlyAmended.getUpdatedOn());
  }

  @Test
  @DisplayName("Should return all VALID claims regardless of status change history")
  void testStatusChangesDoNotPreventRetention() throws Exception {
    // Create claims that all have VALID status (representing claims through status changes)
    createTestClaims(5, ClaimStatus.VALID);

    MvcResult result =
        mockMvc
            .perform(
                get(RETENTION_LOOKUP_ENDPOINT)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER)
                    .param("ufn", UNIQUE_FILE_NUMBER)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total_elements").value(5))
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    ClaimRetentionLookupResultSet response =
        OBJECT_MAPPER.readValue(responseBody, ClaimRetentionLookupResultSet.class);

    // All claims should be returned regardless of any status changes
    assertThat(response.getContent()).hasSize(5);
    assertThat(response.getContent())
        .allMatch(
            claim -> ClaimRetentionLookupClaimDetail.StatusEnum.VALID.equals(claim.getStatus()),
            "All claims should be VALID status");
  }

  @Test
  @DisplayName("Should return empty when no VALID claims exist (retention not triggered)")
  void testNoRetentionWhenNoValidClaims() throws Exception {
    // Create only INVALID claims
    createTestClaims(5, ClaimStatus.INVALID);

    mockMvc
        .perform(
            get(RETENTION_LOOKUP_ENDPOINT)
                .param("office_code", OFFICE_ACCOUNT_NUMBER)
                .param("ufn", UNIQUE_FILE_NUMBER)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.total_elements").value(0))
        .andReturn();
  }

  @Test
  @DisplayName("Should support multiple claims for same office+ufn composite key")
  void testMultipleClaimsPerCompositeKey() throws Exception {
    int claimCount = 7;
    createTestClaims(claimCount, ClaimStatus.VALID);

    MvcResult result =
        mockMvc
            .perform(
                get(RETENTION_LOOKUP_ENDPOINT)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER)
                    .param("ufn", UNIQUE_FILE_NUMBER)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total_elements").value(claimCount))
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    ClaimRetentionLookupResultSet response =
        OBJECT_MAPPER.readValue(responseBody, ClaimRetentionLookupResultSet.class);

    assertThat(response.getContent()).hasSize(claimCount);
  }

  @Test
  @DisplayName("Should return claim_id, status, created_on, updated_on fields only (no PII)")
  void testResponseFieldsNoPii() throws Exception {
    createTestClaims(1, ClaimStatus.VALID);

    MvcResult result =
        mockMvc
            .perform(
                get(RETENTION_LOOKUP_ENDPOINT)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER)
                    .param("ufn", UNIQUE_FILE_NUMBER)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    ClaimRetentionLookupResultSet response =
        OBJECT_MAPPER.readValue(responseBody, ClaimRetentionLookupResultSet.class);

    // Verify required fields exist (retention-relevant fields)
    assertThat(response.getContent().get(0).getClaimId()).isNotNull();
    assertThat(response.getContent().get(0).getStatus())
        .isEqualTo(ClaimRetentionLookupClaimDetail.StatusEnum.VALID);
    assertThat(response.getContent().get(0).getCreatedOn()).isNotNull();
    assertThat(response.getContent().get(0).getUpdatedOn()).isNotNull();

    // Verify the four fields are present (no PII like names, postcodes, case refs, fees)
    var claim = response.getContent().get(0);
    assertThat(claim)
        .extracting("claimId", "status", "createdOn", "updatedOn")
        .doesNotContainNull();
  }

  @Test
  @DisplayName("Should expose only the fields declared in the OpenAPI contract")
  void testResponseExposesOnlyContractFields() throws Exception {
    createTestClaims(2, ClaimStatus.VALID);

    MvcResult result =
        mockMvc
            .perform(
                get(RETENTION_LOOKUP_ENDPOINT)
                    .param("office_code", OFFICE_ACCOUNT_NUMBER)
                    .param("ufn", UNIQUE_FILE_NUMBER)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode root = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());

    assertThat(fieldNamesOf(root))
        .as("response envelope must expose no field beyond the OpenAPI contract")
        .containsExactlyInAnyOrder("content", "total_pages", "total_elements", "number", "size");

    JsonNode content = root.get("content");
    assertThat(content.isArray()).isTrue();
    assertThat(content.size()).isEqualTo(2);

    content.forEach(
        claim ->
            assertThat(fieldNamesOf(claim))
                .as("claim entry must expose no field beyond the OpenAPI contract")
                .containsExactlyInAnyOrder("claim_id", "status", "created_on", "updated_on"));
  }

  // Helper methods

  /** Raw JSON field names of a node, so fields absent from the contract can be detected. */
  private static List<String> fieldNamesOf(JsonNode node) {
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private void createTestClaims(int count, ClaimStatus status) {
    for (int i = 0; i < count; i++) {
      saveClaim(status);
    }
  }

  /**
   * Persists a claim for the test composite key. {@code createdOn}/{@code updatedOn} are managed by
   * Hibernate ({@code @CreationTimestamp}/{@code @UpdateTimestamp}) and therefore cannot be seeded;
   * use {@link #amendClaim(Claim)} to advance a claim's {@code updatedOn}.
   */
  private Claim saveClaim(ClaimStatus status) {
    Claim claim = new Claim();
    claim.setId(Uuid7.timeBasedUuid());
    claim.setSubmission(testSubmission);
    claim.setUniqueFileNumber(UNIQUE_FILE_NUMBER);
    claim.setStatus(status);
    claim.setLineNumber(nextLineNumber++);
    claim.setMatterTypeCode("MATTER_TYPE");
    claim.setFeeCode("FEE_CODE");
    claim.setCreatedByUserId("test-user");
    return claimRepository.saveAndFlush(claim);
  }

  /**
   * Simulates an amendment so Hibernate advances {@code updatedOn} past every earlier write.
   *
   * <p>{@code updatedOn} is never assigned directly because {@code @UpdateTimestamp} overwrites it
   * on flush. Instead a genuinely different value is written so the entity is always dirty; a
   * repeated identical value would be a no-op and would leave {@code updatedOn} unchanged.
   */
  private Claim amendClaim(Claim claim) {
    claim.setUpdatedByUserId("amending-user-" + (++amendmentCount));
    return claimRepository.saveAndFlush(claim);
  }
}
