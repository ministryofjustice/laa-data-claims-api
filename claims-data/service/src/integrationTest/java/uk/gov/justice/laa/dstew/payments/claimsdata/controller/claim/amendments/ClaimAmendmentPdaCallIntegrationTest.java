package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockserver.verify.VerificationTimes;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.helper.MockServerIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;

/**
 * End-to-end integration tests for the amendment-path PDA (Provider Details API) call layer
 * (DSTEW-1646, child DSTEW-1773).
 *
 * <p>These drive the real {@code PATCH /api/v1/submissions/{submissionId}/claims/{claimId}}
 * endpoint through the genuine amendment chain: prepare {@literal ->} validate (including {@code
 * AmendmentExternalValidationStep}, which delegates to the shared claims-validation-core {@code
 * ValidationService} that invokes the outbound {@code getProviderFirmSchedules} HTTP call). The
 * outbound call is stubbed via {@link MockServerIntegrationTest}, so the tests assert the ticket's
 * behaviour purely by observing the MockServer interactions:
 *
 * <ul>
 *   <li><b>Repeated calls</b> - two PDA-relevant amendments with the same {@code officeCode +
 *       effectiveDate} each make an outbound call; schedule responses are not cached.
 *   <li><b>Single attempt</b> - a PDA-relevant amendment makes exactly one outbound call (single
 *       synchronous attempt, no retries).
 *   <li><b>Not triggered</b> - a non-PDA-impacting amendment makes no outbound call (Step 10
 *       receives {@code pda_relevant = false}).
 *   <li><b>Timeout</b> - with the amendment-path read timeout overridden small and the stub delayed
 *       past it, exactly one attempt is made (no retry), the call returns at the timeout (not after
 *       the full response), and the generic PDA technical-error validation issue is surfaced.
 * </ul>
 *
 * <p><b>Request isolation.</b> Each test creates a submission with a unique office code to keep its
 * request data distinct. The configured Provider Details HTTP provider does not cache schedule
 * responses, so repeated validations each make an outbound request.
 *
 * <p><b>Assertion scope.</b> The PDA behaviour under test happens in the validate phase, so the
 * call-count/no-retry assertions observe the MockServer call counts. The timeout additionally
 * asserts the resulting technical-error issue; the amendment commit path is owned by DSTEW-1771.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("Amendment PDA call layer integration test")
class ClaimAmendmentPdaCallIntegrationTest extends AbstractAmendmentPatchIntegrationTest {

  // Claim API date fields are DD/MM/YYYY strings (see ClaimPost/claim_base).
  private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  // The fee code that triggers the PROD effective-date priority in PdaRequestField.
  private static final String PROD_FEE_CODE = "PROD";

  /**
   * Overrides the amendment-path Provider Details read timeout to a small value for this whole
   * class, so the timeout test can trip it with a modest stub delay instead of a multi-second wait.
   * The non-timeout tests stub an immediate response, comfortably inside this budget.
   */
  @DynamicPropertySource
  static void amendmentPdaTimeout(DynamicPropertyRegistry registry) {
    registry.add("laa.dstew.payments.validator.provider-details-api.readTimeoutMs", () -> "1000");
  }

  @Test
  @DisplayName("two amendments with the same office and effective date each make an outbound call")
  void repeatedAmendmentsWithSameKeyEachMakeOutboundCall() throws Exception {
    stubProviderSchedulesOk();

    UUID submissionId = createSubmissionWithUniqueOffice();
    LocalDate sharedStartDate = LocalDate.of(2025, Month.JULY, 31);

    // Two claims under the same office, with the same effective-date-determining state, so both
    // amendments issue the same PDA request (officeCode + effectiveDate).
    Claim firstClaim =
        createAmendableClaim(submissionId, b -> b.feeCode("FEE1").caseStartDate(sharedStartDate));
    Claim secondClaim =
        createAmendableClaim(submissionId, b -> b.feeCode("FEE1").caseStartDate(sharedStartDate));

    ClaimPatch firstPatch = createBasePatch();
    firstPatch.setFeeCode("FEE2");
    ClaimPatch secondPatch = createBasePatch();
    secondPatch.setFeeCode("FEE2");

    performPatch(submissionId, firstClaim.getId(), firstPatch);
    performPatch(submissionId, secondClaim.getId(), secondPatch);

    verifyProviderSchedulesCalled(VerificationTimes.exactly(2));
  }

  @ParameterizedTest
  @EnumSource(PdaImpactingField.class)
  @DisplayName("a PDA-relevant field change makes exactly one attempt (no retry)")
  void pdaRelevantFieldChangeMakesSingleOutboundCall(PdaImpactingField field) throws Exception {
    stubProviderSchedulesOk();

    UUID submissionId = createSubmissionWithUniqueOffice();
    Claim claim = createAmendableClaim(submissionId, field.claimState);

    ClaimPatch patch = createBasePatch();
    field.patchMutator.accept(patch);

    performPatch(submissionId, claim.getId(), patch);

    // Exactly one synchronous attempt; resilience4j pdaRetry.maxAttempts=1 -> no retry.
    verifyProviderSchedulesCalled(VerificationTimes.exactly(1));
  }

  @Test
  @DisplayName("not triggered - a non-PDA-impacting amendment makes no outbound call")
  void notTriggeredNonPdaFieldChangeMakesNoOutboundCall() throws Exception {
    stubProviderSchedulesOk();

    UUID submissionId = createSubmissionWithUniqueOffice();
    Claim claim =
        createAmendableClaim(
            submissionId,
            b ->
                b.feeCode("FEE1")
                    .caseStartDate(LocalDate.of(2098, Month.JANUARY, 1))
                    .matterTypeCode("MTC1"));

    // matterTypeCode is not a PDA-impacting field, so Step 10 receives pda_relevant = false.
    ClaimPatch patch = createBasePatch();
    patch.setMatterTypeCode("MTC2");

    performPatch(submissionId, claim.getId(), patch);

    verifyProviderSchedulesCalled(VerificationTimes.exactly(0));
  }

  @Test
  @DisplayName("timeout - a slow PDA response yields a single attempt and no retry")
  void timeoutSlowResponseMakesSingleAttemptWithNoRetry() throws Exception {
    long responseDelayMs = 2500;
    long readTimeoutMs = 1000; // matches the class-level readTimeoutMs override
    // Upper bound comfortably between the read timeout (~1000ms) and the stub delay (2500ms): a
    // timed-out attempt returns near the timeout, a non-timed-out one waits for the full response.
    long timedOutUpperBoundMs = 2000;
    // Delay the response well past the read timeout so the single attempt is forced to time out.
    stubProviderSchedulesWithDelay(Duration.ofMillis(responseDelayMs));

    UUID submissionId = createSubmissionWithUniqueOffice();
    Claim claim =
        createAmendableClaim(
            submissionId,
            b -> b.feeCode("FEE1").caseStartDate(LocalDate.of(2097, Month.JANUARY, 1)));

    ClaimPatch patch = createBasePatch();
    patch.setFeeCode("FEE2");

    long startNanos = System.nanoTime();
    MvcResult result = performPatch(submissionId, claim.getId(), patch);
    long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

    // The configured external-service timeout is reached: a single attempt is made and no retry is
    // performed (resilience4j pdaRetry.maxAttempts=1).
    verifyProviderSchedulesCalled(VerificationTimes.exactly(1));

    // Proof the amendment-path read timeout is actually applied: the request returns after roughly
    // the read timeout and, crucially, before the stub's much later response - so the attempt timed
    // out rather than waiting for the (successful) response.
    assertThat(elapsedMs)
        .as(
            "PDA call should time out at readTimeoutMs (%dms) and return before the stub's %dms "
                + "response, not wait for it",
            readTimeoutMs, responseDelayMs)
        .isLessThan(timedOutUpperBoundMs);

    // The timeout surfaces as the generic PDA technical-error validation issue (the outcome mapped
    // by DSTEW-1774). Other unrelated schema errors from this deliberately-minimal claim may also
    // be
    // present, so assert the technical error's presence rather than that it is the only error.
    assertThat(result.getResponse().getContentAsString())
        .as("PDA read timeout should surface as the %s technical error", PDA_TECHNICAL_ERROR_CODE)
        .contains(PDA_TECHNICAL_ERROR_CODE);
  }

  // ---------------------------------------------------------------------------
  // PDA-impacting field scenarios (mirrors PdaRequestField's priority-based rules)
  // ---------------------------------------------------------------------------

  /**
   * Each value sets up a claim whose merged state makes the named field PDA-impacting per {@code
   * PdaRequestField}, and patches that field to a changed value so the amendment diff carries it.
   */
  private enum PdaImpactingField {

    // feeCode always impacts the PDA request.
    FEE_CODE(
        b -> b.feeCode("FEE1").caseStartDate(LocalDate.of(2090, Month.JANUARY, 1)),
        p -> p.setFeeCode("FEE2")),

    // caseStartDate impacts unless a PROD fee with a concluded date takes priority.
    CASE_START_DATE(
        b -> b.feeCode("FEE1").caseStartDate(LocalDate.of(2091, Month.JANUARY, 1)),
        p -> p.setCaseStartDate(LocalDate.of(2091, Month.JUNE, 1).format(API_DATE))),

    // caseConcludedDate impacts when the (merged) fee code is PROD.
    CASE_CONCLUDED_DATE_PROD(
        b ->
            b.feeCode(PROD_FEE_CODE)
                .caseStartDate(LocalDate.of(2092, Month.JANUARY, 1))
                .caseConcludedDate(LocalDate.of(2092, Month.FEBRUARY, 1)),
        p -> p.setCaseConcludedDate(LocalDate.of(2092, Month.MARCH, 1).format(API_DATE))),

    // representationOrderDate impacts when there is no caseStartDate (and no PROD+concluded).
    REPRESENTATION_ORDER_DATE(
        b -> b.feeCode("FEE1").representationOrderDate(LocalDate.of(2093, Month.JANUARY, 1)),
        p -> p.setRepresentationOrderDate(LocalDate.of(2093, Month.JUNE, 1).format(API_DATE))),

    // uniqueFileNumber impacts when there is no caseStartDate and no representationOrderDate. A UFN
    // encodes the date used to derive the effective date (DDMMYY/NNN), so both values must be valid
    // UFNs for the call layer to build a request.
    UNIQUE_FILE_NUMBER(
        b -> b.feeCode("FEE1").uniqueFileNumber("010125/001"),
        p -> p.setUniqueFileNumber("020125/001"));

    private final Consumer<Claim.ClaimBuilder> claimState;
    private final Consumer<ClaimPatch> patchMutator;

    PdaImpactingField(Consumer<Claim.ClaimBuilder> claimState, Consumer<ClaimPatch> patchMutator) {
      this.claimState = claimState;
      this.patchMutator = patchMutator;
    }
  }
}
