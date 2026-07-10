package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;

/**
 * End-to-end integration tests for how PDA (Provider Details API) outcomes are surfaced in the
 * amendment validation response (DSTEW-1646, child DSTEW-1774).
 *
 * <p>Complements {@code ClaimAmendmentPdaCallIntegrationTest} (the call-layer contract: cache,
 * timeout, no-retry). Here we drive the real {@code PATCH
 * /api/v1/submissions/{submissionId}/claims/{claimId}} endpoint with a PDA-relevant amendment and
 * stub the PDA {@code /schedules} response (and, where relevant, the Fee Scheme Platform
 * fee-details response) to force each outcome, then assert the resulting validation code is present
 * in the {@code 400} amendment-validation response.
 *
 * <p><b>Scope.</b> This asserts only that the expected code is <em>triggered and returned</em>. It
 * deliberately does not assert terminal/precedence behaviour (whether a technical failure
 * suppresses other collected messages) - that classification is handled elsewhere in the
 * orchestration and is out of scope for these tests. All collected messages are expected to be
 * returned, so assertions check for the <em>presence</em> of the target code alongside any
 * unrelated schema noise from the deliberately-minimal claim.
 *
 * <p><b>Category-of-law recipe.</b> claims-validation-core authorises the claim's category of law
 * by intersecting the FSP fee-details {@code categoryOfLawCodes} (for the claim's fee code) with
 * the categories extracted from the PDA schedule lines ({@code
 * schedules[].scheduleLines[].categoryOfLaw}, exact, case-sensitive). The default fixtures both use
 * {@code "string"} (so they intersect and pass); the category-mismatch fixture supplies a different
 * provider category so the intersection is empty and {@code
 * INVALID_CATEGORY_OF_LAW_NOT_AUTHORISED_FOR_PROVIDER} fires.
 *
 * <p><b>Technical failures.</b> HTTP 5xx, a malformed/undecodable body and a dropped connection all
 * map to the generic {@code TECHNICAL_ERROR_PROVIDER_DETAILS_API} in the library (timeout is
 * covered separately by {@code ClaimAmendmentPdaCallIntegrationTest}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("Amendment PDA outcome mapping integration test")
class ClaimAmendmentPdaOutcomeIntegrationTest extends AbstractAmendmentPatchIntegrationTest {

  // Existing claims-validation-core code reused by the amendment flow (no new codes introduced).
  private static final String CATEGORY_OF_LAW_NOT_AUTHORISED_CODE =
      "INVALID_CATEGORY_OF_LAW_NOT_AUTHORISED_FOR_PROVIDER";

  @Test
  @DisplayName("category of law not authorised - collects INVALID_CATEGORY_OF_LAW_NOT_AUTHORISED")
  void categoryOfLawNotAuthorisedCollectsValidationMessage() throws Exception {
    // Provider schedule authorises a category that does not intersect the fee-details category
    // (["string"]), so the category-of-law authorisation fails.
    stubProviderSchedules("provider-details/get-firm-schedules-category-mismatch-200.json");

    UUID submissionId = createSubmissionWithUniqueOffice();
    UUID claimId = createAmendableClaim(submissionId);

    MvcResult result = performPatch(submissionId, claimId, feeCodeChangePatch());

    assertBadRequestContaining(result, CATEGORY_OF_LAW_NOT_AUTHORISED_CODE);
  }

  @Test
  @DisplayName("PDA HTTP 5xx - maps to TECHNICAL_ERROR_PROVIDER_DETAILS_API")
  void httpServerErrorMapsToTechnicalError() throws Exception {
    stubProviderSchedulesStatus(HttpStatus.SERVICE_UNAVAILABLE.value());

    UUID submissionId = createSubmissionWithUniqueOffice();
    UUID claimId = createAmendableClaim(submissionId);

    MvcResult result = performPatch(submissionId, claimId, feeCodeChangePatch());

    assertBadRequestContaining(result, PDA_TECHNICAL_ERROR_CODE);
  }

  @Test
  @DisplayName("PDA malformed response - maps to TECHNICAL_ERROR_PROVIDER_DETAILS_API")
  void malformedResponseMapsToTechnicalError() throws Exception {
    stubProviderSchedulesRawBody("{ this is not valid provider-schedules json ");

    UUID submissionId = createSubmissionWithUniqueOffice();
    UUID claimId = createAmendableClaim(submissionId);

    MvcResult result = performPatch(submissionId, claimId, feeCodeChangePatch());

    assertBadRequestContaining(result, PDA_TECHNICAL_ERROR_CODE);
  }

  @Test
  @DisplayName("PDA connection failure - maps to TECHNICAL_ERROR_PROVIDER_DETAILS_API")
  void connectionFailureMapsToTechnicalError() throws Exception {
    stubProviderSchedulesConnectionDrop();

    UUID submissionId = createSubmissionWithUniqueOffice();
    UUID claimId = createAmendableClaim(submissionId);

    MvcResult result = performPatch(submissionId, claimId, feeCodeChangePatch());

    assertBadRequestContaining(result, PDA_TECHNICAL_ERROR_CODE);
  }

  @Test
  @Disabled(
      "INVALID_AREA_OF_LAW_FOR_PROVIDER does not exist in claims-validation-core: the "
          + "EffectiveCategoryOfLawClaimValidator never reads any area-of-law field from the PDA "
          + "response, so the library cannot emit this code. Re-enable once the area-of-law check "
          + "exists (raise with the DSTEW-1774 owner / BA - the AC may be incorrect).")
  @DisplayName("area of law not authorised - collects INVALID_AREA_OF_LAW_FOR_PROVIDER (gap)")
  void areaOfLawNotAuthorisedCollectsValidationMessage() {
    // Intentionally empty: documents a spec-vs-library gap (see @Disabled reason).
  }

  // ---------------------------------------------------------------------------
  // Fixtures / helpers
  // ---------------------------------------------------------------------------

  /** Asserts the amendment response is {@code 400} and its body contains the given code. */
  private void assertBadRequestContaining(MvcResult result, String expectedCode) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(result.getResponse().getContentAsString())
        .as("amendment validation response should contain %s", expectedCode)
        .contains(expectedCode);
  }

  /**
   * Creates a VALID (amendable) claim whose fee code and effective date make the amendment
   * PDA-relevant (a fee-code change always impacts the PDA request).
   */
  private UUID createAmendableClaim(UUID submissionId) {
    return createAmendableClaim(
        submissionId, b -> b.feeCode("FEE1").caseStartDate(LocalDate.of(2099, Month.JANUARY, 1)));
  }

  /** A patch carrying valid amendment metadata plus a PDA-relevant fee-code change. */
  private ClaimPatch feeCodeChangePatch() {
    ClaimPatch patch = metadataPatch();
    patch.setFeeCode("FEE2");
    return patch;
  }
}
