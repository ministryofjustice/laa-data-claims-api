package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.verify.VerificationTimes;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * End-to-end integration tests for the fee-code Area-of-Law amendment gate (DSTEW-1768).
 *
 * <p>These drive the real {@code PATCH /api/v1/submissions/{submissionId}/claims/{claimId}}
 * endpoint with the amendments feature flag enabled, so the genuine amendment chain runs through
 * {@code AmendmentExternalValidationStep}, which delegates to the shared claims-validation-core
 * {@code ValidationService}. That library resolves the fee code's Area of Law from the Fee Scheme
 * {@code /api/v2/fee-details/{feeCode}} endpoint (stubbed via {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.helper.MockServerIntegrationTest MockServer}) and
 * surfaces it on the result's {@code resolvedData}. The step rejects a fee-code change whose
 * resolved Area of Law differs from the claim's.
 *
 * <p>The seeded {@code CLAIM_1} belongs to a {@code LEGAL_HELP} submission and carries the
 * schema-required fields (client, summary fee) needed for a clean amendment, mirroring the
 * happy-path suite. Each scenario stubs the fee-details Area of Law to drive a same-AoL (accepted)
 * or different-AoL (rejected) outcome and asserts the persistence side-effects directly.
 *
 * <p><b>Fee-code cache isolation.</b> claims-validation-core caches fee-details responses per fee
 * code on a JVM-wide singleton provider bean, so (as with the PDA suite's per-test office codes)
 * each test amends to a unique fee code to keep its cached area-of-law entry from colliding with
 * another test's.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("Amendment fee-code Area of Law integration test")
class ClaimAmendmentFeeCodeAreaOfLawIntegrationTest extends AbstractAmendmentPatchIntegrationTest {

  private static final String AREA_OF_LAW_REJECTION_CODE = "INVALID_FEE_CODE_AREA_OF_LAW_CHANGE";
  private static final String FEE_SCHEME_TECHNICAL_ERROR_CODE = "TECHNICAL_ERROR_FEE_SCHEME_API";
  // The genuine Fee Scheme name-form value matching the seeded LEGAL_HELP submission.
  private static final String LEGAL_HELP_AREA_OF_LAW = "LEGAL_HELP";

  // Static so amended fee codes are unique across every test in the JVM, keeping the per-JVM
  // fee-details cache (keyed on fee code) isolated between tests.
  private static final AtomicInteger FEE_CODE_SEQ = new AtomicInteger();

  private static String uniqueFeeCode() {
    return "AOL" + FEE_CODE_SEQ.incrementAndGet();
  }

  /**
   * Shrinks the fee-scheme read timeout so the timeout scenario can trip it with a modest stub
   * delay, and pins the fee-scheme retry to a single attempt so failure/timeout call counts are
   * deterministic. The non-timeout scenarios stub immediate responses, comfortably inside the
   * budget.
   */
  @DynamicPropertySource
  static void feeSchemeTimeoutAndRetry(DynamicPropertyRegistry registry) {
    registry.add("laa.dstew.payments.validator.fee-scheme-platform-api.readTimeoutMs", () -> "500");
    registry.add("resilience4j.retry.instances.feeSchemeRetry.maxAttempts", () -> "1");
  }

  private ClaimPatch feeCodePatch(Long version, String feeCode) {
    ClaimPatch patch = metadataPatch();
    patch.setVersion(version);
    patch.setFeeCode(feeCode);
    return patch;
  }

  @Test
  @DisplayName("same Area of Law fee code change is accepted and the amendment commits")
  void sameAreaOfLawFeeCodeChangeCommits() throws Exception {
    stubProviderSchedulesOk();
    // The seeded submission is LEGAL_HELP; report the same Area of Law (in the genuine name form
    // the
    // Fee Scheme API returns) for the new fee code, so the resolved Area of Law is an exact match
    // and the gate does not fire - a different fee code within the same Area of Law is allowed.
    stubFeeDetailsAreaOfLaw(LEGAL_HELP_AREA_OF_LAW);

    Claim seeded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    seeded.setStatus(ClaimStatus.VALID);
    String originalFeeCode = seeded.getFeeCode();
    Claim savedClaim = claimRepository.saveAndFlush(seeded);

    String amendedFeeCode = uniqueFeeCode();
    ClaimPatch patch = metadataPatch();
    patch.setVersion(savedClaim.getVersion());
    patch.setFeeCode(amendedFeeCode);

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value());

    // The amendment was persisted and the fee code applied.
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID)).hasSize(1);
    Claim amended = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(amended.getFeeCode()).isEqualTo(amendedFeeCode).isNotEqualTo(originalFeeCode);
    assertThat(amended.isAmended()).isTrue();
  }

  @Test
  @DisplayName("different Area of Law fee code change is rejected and nothing is persisted")
  void differentAreaOfLawFeeCodeChangeIsRejectedAndNothingPersisted() throws Exception {
    stubProviderSchedulesOk();
    // The resolved Area of Law of the new fee code (CRIME_LOWER) differs from the claim's
    // (LEGAL_HELP), so the terminal gate rejects the amendment.
    stubFeeDetailsAreaOfLaw("CRIME_LOWER");

    Claim seeded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    seeded.setStatus(ClaimStatus.VALID);
    String originalFeeCode = seeded.getFeeCode();
    Claim savedClaim = claimRepository.saveAndFlush(seeded);

    ClaimPatch patch = metadataPatch();
    patch.setVersion(savedClaim.getVersion());
    patch.setFeeCode(uniqueFeeCode());

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    // Terminal, fatal rejection -> HTTP 400 carrying the area-of-law error code.
    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(result.getResponse().getContentAsString()).contains(AREA_OF_LAW_REJECTION_CODE);

    // Nothing persisted: no amendment audit row and the fee code is unchanged.
    List<ClaimAmendment> amendments =
        claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID);
    assertThat(amendments).isEmpty();
    Claim unchanged = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(unchanged.getFeeCode()).isEqualTo(originalFeeCode);
    assertThat(unchanged.isAmended()).isFalse();
  }

  @Test
  @DisplayName("a Fee Scheme API 500 surfaces the technical error and nothing is persisted")
  void feeSchemeApiServerErrorReturnsControlledNoSave() throws Exception {
    stubProviderSchedulesOk();
    // The Fee Scheme API is unavailable: fee-details returns 500 so the Area of Law cannot be
    // resolved. The library maps this to the fee-scheme technical error (the controlled no-save
    // path) rather than throwing, so the amendment is rejected and nothing is persisted.
    stubFeeDetailsStatus(500);

    Claim seeded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    seeded.setStatus(ClaimStatus.VALID);
    String originalFeeCode = seeded.getFeeCode();
    Claim savedClaim = claimRepository.saveAndFlush(seeded);

    ClaimPatch patch = metadataPatch();
    patch.setVersion(savedClaim.getVersion());
    patch.setFeeCode(uniqueFeeCode());

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    // The technical failure is surfaced as a validation error (non-fatal ERROR -> HTTP 400).
    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(result.getResponse().getContentAsString()).contains(FEE_SCHEME_TECHNICAL_ERROR_CODE);

    // Nothing persisted: no amendment audit row and the fee code is unchanged.
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID)).isEmpty();
    Claim unchanged = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(unchanged.getFeeCode()).isEqualTo(originalFeeCode);
    assertThat(unchanged.isAmended()).isFalse();
  }

  @Test
  @DisplayName("a Fee Scheme API 404 surfaces the technical error and nothing is persisted")
  void feeSchemeApiNotFoundReturnsControlledNoSave() throws Exception {
    stubProviderSchedulesOk();
    // The fee code is not found: fee-details returns 404 so the Area of Law cannot be resolved.
    // The library maps this to the fee-scheme technical error (the controlled no-save path).
    stubFeeDetailsStatus(404);

    Claim seeded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    seeded.setStatus(ClaimStatus.VALID);
    String originalFeeCode = seeded.getFeeCode();
    Claim savedClaim = claimRepository.saveAndFlush(seeded);

    MvcResult result =
        performPatch(
            SUBMISSION_1_ID, CLAIM_1_ID, feeCodePatch(savedClaim.getVersion(), uniqueFeeCode()));

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(result.getResponse().getContentAsString()).contains(FEE_SCHEME_TECHNICAL_ERROR_CODE);

    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID)).isEmpty();
    Claim unchanged = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(unchanged.getFeeCode()).isEqualTo(originalFeeCode);
    assertThat(unchanged.isAmended()).isFalse();
  }

  @Test
  @DisplayName("a Fee Scheme API timeout surfaces the technical error and nothing is persisted")
  void feeSchemeApiTimeoutReturnsControlledNoSave() throws Exception {
    stubProviderSchedulesOk();
    long responseDelayMs = 3000;
    // The read timeout is 500ms (see feeSchemeTimeoutAndRetry). A pricing-impacting fee-code change
    // looks the code up twice (context build + category validator), each timing out at ~500ms, so a
    // timed-out request returns well under the 3000ms stub delay; waiting for even one full
    // response
    // would take at least that long.
    stubFeeDetailsWithDelay(Duration.ofMillis(responseDelayMs));

    Claim seeded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    seeded.setStatus(ClaimStatus.VALID);
    String originalFeeCode = seeded.getFeeCode();
    Claim savedClaim = claimRepository.saveAndFlush(seeded);

    long startNanos = System.nanoTime();
    MvcResult result =
        performPatch(
            SUBMISSION_1_ID, CLAIM_1_ID, feeCodePatch(savedClaim.getVersion(), uniqueFeeCode()));
    long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(result.getResponse().getContentAsString()).contains(FEE_SCHEME_TECHNICAL_ERROR_CODE);

    // Proof the read timeout was applied: the request returns before the stub's delayed response.
    assertThat(elapsedMs).isLessThan(responseDelayMs);

    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID)).isEmpty();
    Claim unchanged = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(unchanged.getFeeCode()).isEqualTo(originalFeeCode);
    assertThat(unchanged.isAmended()).isFalse();
  }

  @Test
  @DisplayName("caching - the same fee code is looked up once across two amendments")
  void sameFeeCodeIsLookedUpOnce() throws Exception {
    stubProviderSchedulesOk();
    stubFeeDetailsAreaOfLaw(LEGAL_HELP_AREA_OF_LAW);

    UUID submissionId = createSubmissionWithUniqueOffice();
    Claim claimA = createAmendableClaim(submissionId, b -> b.feeCode("ORIGA"));
    Claim claimB = createAmendableClaim(submissionId, b -> b.feeCode("ORIGB"));

    // Both amendments target the same (unique, previously-uncached) fee code.
    String targetFeeCode = uniqueFeeCode();
    ClaimPatch patchA = feeCodePatch(claimA.getVersion(), targetFeeCode);
    ClaimPatch patchB = feeCodePatch(claimB.getVersion(), targetFeeCode);
    performPatch(submissionId, claimA.getId(), patchA);
    performPatch(submissionId, claimB.getId(), patchB);

    // The second amendment - and the category validator's repeat lookup within each amendment - are
    // served from the positive cache, so the fee code is fetched exactly once.
    verifyFeeDetailsCalled(VerificationTimes.exactly(1));
  }

  @Test
  @DisplayName("caching - distinct fee codes are looked up separately (not conflated)")
  void distinctFeeCodesAreLookedUpSeparately() throws Exception {
    stubProviderSchedulesOk();
    stubFeeDetailsAreaOfLaw(LEGAL_HELP_AREA_OF_LAW);

    UUID submissionId = createSubmissionWithUniqueOffice();
    Claim claimA = createAmendableClaim(submissionId, b -> b.feeCode("ORIGC"));
    Claim claimB = createAmendableClaim(submissionId, b -> b.feeCode("ORIGD"));

    // Two different, previously-uncached fee codes.
    performPatch(submissionId, claimA.getId(), feeCodePatch(claimA.getVersion(), uniqueFeeCode()));
    performPatch(submissionId, claimB.getId(), feeCodePatch(claimB.getVersion(), uniqueFeeCode()));

    // Each distinct fee code is cached independently -> one outbound lookup each.
    verifyFeeDetailsCalled(VerificationTimes.exactly(2));
  }

  @Test
  @DisplayName("caching - per fee code: a valid code is cached but a not-found code is re-fetched")
  void perFeeCodePositiveCachedNotFoundNotCached() throws Exception {
    stubProviderSchedulesOk();

    UUID submissionId = createSubmissionWithUniqueOffice();
    Claim notFoundClaim1 = createAmendableClaim(submissionId, b -> b.feeCode("ORIGE"));
    Claim notFoundClaim2 = createAmendableClaim(submissionId, b -> b.feeCode("ORIGF"));
    Claim validClaim1 = createAmendableClaim(submissionId, b -> b.feeCode("ORIGG"));
    Claim validClaim2 = createAmendableClaim(submissionId, b -> b.feeCode("ORIGH"));

    // A not-found fee code, amended twice.
    String notFoundFeeCode = uniqueFeeCode();
    stubFeeDetailsStatus(404);
    performPatch(
        submissionId,
        notFoundClaim1.getId(),
        feeCodePatch(notFoundClaim1.getVersion(), notFoundFeeCode));
    performPatch(
        submissionId,
        notFoundClaim2.getId(),
        feeCodePatch(notFoundClaim2.getVersion(), notFoundFeeCode));

    // A valid fee code, amended twice.
    String validFeeCode = uniqueFeeCode();
    stubFeeDetailsAreaOfLaw(LEGAL_HELP_AREA_OF_LAW);
    performPatch(
        submissionId, validClaim1.getId(), feeCodePatch(validClaim1.getVersion(), validFeeCode));
    performPatch(
        submissionId, validClaim2.getId(), feeCodePatch(validClaim2.getVersion(), validFeeCode));

    // Positive result is cached: the valid code is fetched exactly once across both amendments.
    verifyFeeDetailsCalledForFeeCode(validFeeCode, VerificationTimes.exactly(1));

    // The not-found code is re-fetched on the second amendment (it is not served from a cache),
    // so it is looked up more than once. NOTE: claims-validation-core documents a short-lived
    // negative cache, but in practice a 404 is raised as an error (not an empty result) so the
    // negative-cache path is never taken. This assertion pins that observed behaviour and flags
    // the gap - if a negative cache is added upstream this expectation should drop to exactly(1).
    verifyFeeDetailsCalledForFeeCode(notFoundFeeCode, VerificationTimes.atLeast(2));
  }
}
