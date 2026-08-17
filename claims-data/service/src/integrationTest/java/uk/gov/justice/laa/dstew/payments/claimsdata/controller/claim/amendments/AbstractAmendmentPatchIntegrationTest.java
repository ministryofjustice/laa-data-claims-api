package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.helper.MockServerIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimAmendmentPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Shared scaffolding for amendment PATCH-endpoint integration tests that exercise the external PDA
 * (Provider Details API) path via MockServer.
 *
 * <p>Extends {@link MockServerIntegrationTest} (which owns the external-HTTP stub/verify helpers)
 * and adds the pieces common to the PDA call-layer and PDA outcome-mapping suites: enabling the
 * amendments feature flag, seeding claims data, stubbing the Fee Scheme Platform calls, creating a
 * uniquely-officed submission and an amendable claim, and driving the PATCH endpoint.
 *
 * <p>It is intentionally a separate base rather than folded into {@link MockServerIntegrationTest},
 * because that class's other (non-PATCH) subclasses perform their own seeding in
 * {@code @BeforeEach}; a shared seeding hook there would double-seed and clash. Keeping it here
 * scopes the amendment-flag toggle and seeding to just the PATCH-driven PDA suites.
 */
abstract class AbstractAmendmentPatchIntegrationTest extends MockServerIntegrationTest {

  /** Governed amendment-metadata reference codes seeded by Flyway migration V41. */
  protected static final String REQUESTED_BY_PROVIDER = "PROVIDER";

  protected static final String REASON_PROVIDER_ERROR = "PROVIDER_ERROR";
  protected static final UUID VALID_USER_UUID =
      UUID.fromString("0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e");

  /** Provider-facing amendment user id used in amendment tests (string form). */
  protected static final String AMENDMENT_USER_ID = "00000000-0000-0000-0000-000000000001";

  /** The generic technical-error code claims-validation-core emits on any PDA call failure. */
  protected static final String PDA_TECHNICAL_ERROR_CODE = "TECHNICAL_ERROR_PROVIDER_DETAILS_API";

  protected static final String PATCH_A_CLAIM_ENDPOINT =
      API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}";

  private static final String CREATED_BY = "amendment-pda-integration-test";

  // Static so office codes are unique across every subclass in the JVM, keeping the per-JVM PDA
  // cache (keyed on office) isolated between tests and classes.
  private static final AtomicInteger OFFICE_SEQ = new AtomicInteger();

  // Static, monotonically increasing line numbers so multiple claims created within the same
  // submission never collide on the uq_claim_submission_line_number unique constraint.
  private static final AtomicInteger LINE_SEQ = new AtomicInteger();

  // Serialises the patch omitting null fields, so only the keys we explicitly set are sent (an
  // explicit null would be read by the service as "clear this field").
  protected static final ObjectMapper PATCH_MAPPER = nonNullMapper();

  private static ObjectMapper nonNullMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    return mapper;
  }

  @Autowired protected ClaimsApiProperties claimsApiProperties;

  private boolean originalAmendmentFlag;

  @BeforeEach
  void enableAmendmentsSeedAndStubFeeScheme() throws IOException {
    enableAmendmentsFlag();
    seedClaimsData();
    // The non-PDA external calls succeed with the default fixtures; each test stubs the PDA
    // /schedules call itself to drive the behaviour under test.
    stubFeeSchemeEndpoints();
  }

  @AfterEach
  void restoreAmendmentsFlag() {
    claimsApiProperties.getAmendments().setEnabled(String.valueOf(originalAmendmentFlag));
  }

  protected void enableAmendmentsFlag() {
    originalAmendmentFlag = claimsApiProperties.getAmendments().isEnabled();
    claimsApiProperties.getAmendments().setEnabled("true");
  }

  /**
   * Creates a submission under the seeded bulk submission with a unique office code, giving each
   * test an isolated PDA cache key space.
   *
   * @return the new submission id
   */
  protected UUID createSubmissionWithUniqueOffice() {
    UUID id = Uuid7.timeBasedUuid();
    Submission submission =
        Submission.builder()
            .id(id)
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber("PDAOF" + OFFICE_SEQ.incrementAndGet())
            .submissionPeriod("JAN-2025")
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .createdByUserId(CREATED_BY)
            .providerUserId(bulkSubmission.getCreatedByUserId())
            .numberOfClaims(0)
            .createdOn(CREATED_ON)
            .build();
    submissionRepository.saveAndFlush(submission);
    return id;
  }

  /**
   * Creates a VALID (amendable) claim under the given submission, applying the supplied state so a
   * test can control the effective-date-determining fields.
   *
   * @param submissionId the owning submission
   * @param state customises the claim builder (e.g. fee code and dates)
   * @return the new claim
   */
  protected Claim createAmendableClaim(UUID submissionId, Consumer<Claim.ClaimBuilder> state) {
    Claim.ClaimBuilder builder =
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .submission(submissionRepository.getReferenceById(submissionId))
            .status(ClaimStatus.VALID)
            // Unique per submission to satisfy uq_claim_submission_line_number: tests create
            // several
            // amendable claims in one submission.
            .lineNumber(LINE_SEQ.incrementAndGet())
            .caseReferenceNumber("PDA-CRN")
            .matterTypeCode("MTC")
            .createdByUserId(CREATED_BY)
            .createdOn(CREATED_ON);
    state.accept(builder);
    return claimRepository.saveAndFlush(builder.build());
  }

  /**
   * A patch carrying valid amendment metadata (requested-by, reason and user id) plus the current
   * claim version so it passes the early version gate. Claims created by {@link
   * #createAmendableClaim} are freshly inserted at version {@code 0}. Tests add the field change
   * under test on top.
   *
   * @return a metadata-only claim patch
   */
  protected ClaimPatch createBasePatch() {
    ClaimPatch patch = new ClaimPatch();
    patch.setVersion(0L);
    patch.setAmendmentRequestedBy(REQUESTED_BY_PROVIDER);
    patch.setAmendmentReasonCode(REASON_PROVIDER_ERROR);
    patch.setAmendmentUserId(VALID_USER_UUID);
    return patch;
  }

  /** Thin assertion helper for HTTP response status checks used across tests. */
  protected void assertResponseStatus(
      MvcResult result, org.springframework.http.HttpStatus expected) {
    assertThat(result.getResponse().getStatus()).isEqualTo(expected.value());
  }

  /** Thin assertion helper to assert the response body contains the given fragment. */
  protected void assertResponseContains(MvcResult result, String expectedFragment)
      throws java.io.UnsupportedEncodingException {
    String body = result.getResponse().getContentAsString();
    assertThat(body).contains(expectedFragment);
  }

  /**
   * Asserts no amendment audit row was written for the given claim and the claim's amended flag and
   * version remain unchanged.
   */
  protected void assertNoAmendmentWritten(UUID claimId, Long originalVersion) {
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(claimId)).isEmpty();
    Claim after = claimRepository.findById(claimId).orElseThrow();
    assertThat(after.isAmended()).isFalse();
    assertThat(after.getVersion()).isEqualTo(originalVersion);
  }

  /**
   * Asserts an amendment committed: a 204 response, at least one audit row exists and the claim is
   * marked amended. Does not assert an exact version increment.
   */
  protected void assertAmendmentCommittedGeneric(UUID claimId) {
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(claimId)).isNotEmpty();
    Claim after = claimRepository.findById(claimId).orElseThrow();
    assertThat(after.isAmended()).isTrue();
  }

  /**
   * Asserts an amendment committed and that the claim's optimistic-lock version advanced exactly by
   * one compared with the supplied originalVersion. Also asserts the response status is 204.
   */
  protected void assertAmendmentCommittedVersioned(
      org.springframework.test.web.servlet.MvcResult result, UUID claimId, Long originalVersion)
      throws Exception {
    assertResponseStatus(result, HttpStatus.NO_CONTENT);
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(claimId)).hasSize(1);
    Claim after = claimRepository.findById(claimId).orElseThrow();
    assertThat(after.isAmended()).isTrue();
    assertThat(after.getVersion()).isEqualTo(originalVersion + 1);
  }

  /**
   * Performs the amendment PATCH for the given submission/claim with the supplied patch body.
   *
   * @return the completed {@link MvcResult}
   */
  protected MvcResult performPatch(UUID submissionId, UUID claimId, ClaimPatch patch)
      throws Exception {
    return mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, submissionId, claimId)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PATCH_MAPPER.writeValueAsString(patch)))
        .andReturn();
  }

  /**
   * Performs the amendment PATCH for the given submission/claim with a ClaimAmendmentPatch body.
   *
   * @return the completed {@link MvcResult}
   */
  protected MvcResult performAmendmentPatch(
      UUID submissionId, UUID claimId, ClaimAmendmentPatch amendmentPatch) throws Exception {
    return mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, submissionId, claimId)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PATCH_MAPPER.writeValueAsString(amendmentPatch)))
        .andReturn();
  }

  /**
   * Create a CalculatedFeeDetail row for the given claim with parameterised values. Tests that
   * previously duplicated near-identical builders should use this helper to avoid repetition.
   */
  protected void createCalculatedFeeDetail(
      Claim claim,
      java.math.BigDecimal totalAmount,
      boolean escapeCaseFlag,
      Instant createdOn,
      String feeCode) {

    ClaimSummaryFee summaryFee =
        claimSummaryFeeRepository
            .findByClaimId(claim.getId())
            .orElseGet(
                () -> {
                  ClaimSummaryFee newFee =
                      ClaimSummaryFee.builder()
                          .claim(claim)
                          .id(Uuid7.timeBasedUuid())
                          .createdByUserId("Test")
                          .build();
                  return claimSummaryFeeRepository.saveAndFlush(newFee);
                });

    CalculatedFeeDetail cfd = new CalculatedFeeDetail();
    cfd.setId(Uuid7.timeBasedUuid());
    cfd.setClaim(claim);
    cfd.setEscapeCaseFlag(escapeCaseFlag);
    cfd.setCreatedOn(createdOn);
    cfd.setFeeCode(feeCode);
    cfd.setCreatedByUserId("Test");
    cfd.setClaimSummaryFee(summaryFee);
    cfd.setTotalAmount(totalAmount);

    calculatedFeeDetailRepository.saveAndFlush(cfd);
  }

  /**
   * Convenience overload matching existing test usages that assume a 100.00 baseline fee code
   * FEE-123
   */
  protected void createCalculatedFeeDetail(Claim claim, boolean escapeCaseFlag, Instant createdOn) {
    createCalculatedFeeDetail(
        claim, BigDecimal.valueOf(100.00), escapeCaseFlag, createdOn, "FEE-123");
  }

  /** Convenience helper for the canonical baseline used in history tests. */
  protected void createBaselineCalculatedFeeDetail(Claim claim) {
    createCalculatedFeeDetail(
        claim,
        BigDecimal.valueOf(100.00),
        false,
        Instant.now().minus(1, ChronoUnit.DAYS),
        "FEE-123");
  }
}
