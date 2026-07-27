package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getAssessmentPost;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * Cross-feature optimistic-concurrency test for the parent OCC story (DSTEW-1658 Test Note):
 *
 * <blockquote>
 *
 * "Coordinate with assessment OCC/version work so an assessment submitted while an amendment screen
 * is open invalidates the stale amendment."
 *
 * </blockquote>
 *
 * <p>It drives the real endpoints end to end, exactly as a caseworker race would occur:
 *
 * <ol>
 *   <li>a claim exists in the amendable {@code VALID} state;
 *   <li>AaBC loads the claim and captures its {@code claim.version} (the value the amend screen
 *       holds);
 *   <li>an <b>assessment</b> of the same claim is submitted via the real {@code POST
 *       /claims/{claimId}/assessments} endpoint - a version-advancing action per the parent story;
 *   <li>the amendment is submitted via {@code PATCH ...} carrying the version captured at step 2.
 * </ol>
 *
 * <p>Per the parent Core Contract, the amendment must be rejected with {@code 409 Conflict} /
 * {@code CLAIM_VERSION_CONFLICT}, because the assessment changed the claim after it was loaded, so
 * the amendment is based on outdated claim information and must save nothing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("Amendment vs concurrent assessment OCC integration test")
class AmendmentVersusAssessmentOccIntegrationTest extends AbstractAmendmentPatchIntegrationTest {

  private static final String POST_AN_ASSESSMENT_ENDPOINT =
      API_URI_PREFIX + "/claims/{claimId}/assessments";

  @Test
  @Disabled(
      "CONFIRMED GAP (DSTEW-1658 coordination): the assessment path updates the claim via the bulk"
          + " JPQL ClaimRepository.updateAssessmentStatus, which does not increment @Version (and"
          + " only runs for the first assessment). An assessment therefore does NOT advance"
          + " claim.version, so a concurrent assessment does not invalidate a stale amendment. This"
          + " test asserts the required behaviour and will pass once the assessment OCC/version work"
          + " advances claim.version on every assessment. Empirically verified: version was 1 before"
          + " and after the assessment.")
  @DisplayName(
      "an assessment submitted after the claim is loaded invalidates a stale amendment with 409 "
          + "Conflict")
  void assessmentAfterLoadInvalidatesStaleAmendmentWithConflict() throws Exception {
    // Clean PDA response so, if the amendment reached external validation, it would not add noise.
    stubProviderSchedulesOk();

    // (1) The claim is in the amendable state. Settle the version so it is clean before we read it.
    Claim seeded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    seeded.setStatus(ClaimStatus.VALID);
    Claim amendable = claimRepository.saveAndFlush(seeded);

    // (2) AaBC loads the claim and captures the version the amend screen will submit with.
    Long versionLoadedByAmendScreen = amendable.getVersion();

    // (3) A concurrent assessment of the same claim is submitted via the real endpoint. Per the
    // parent story this is a version-advancing action, so it must move claim.version on.
    mockMvc
        .perform(
            post(POST_AN_ASSESSMENT_ENDPOINT, CLAIM_1_ID)
                .content(OBJECT_MAPPER.writeValueAsString(getAssessmentPost()))
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isCreated());

    Long versionAfterAssessment = claimRepository.findById(CLAIM_1_ID).orElseThrow().getVersion();

    // The assessment is a claim-changing action, so it must have advanced the version - otherwise
    // the early version gate cannot detect that the amendment is now stale.
    assertThat(versionAfterAssessment)
        .as(
            "an assessment must advance claim.version so a concurrent amendment is detected as stale")
        .isGreaterThan(versionLoadedByAmendScreen);

    // (4) The amendment is submitted with the pre-assessment version the screen loaded. Use a
    // non-pricing field change (client name) so the assessed-claim pricing guard is not the reason
    // for rejection - the rejection must come from the stale version alone.
    ClaimPatch patch = metadataPatch();
    patch.setVersion(versionLoadedByAmendScreen);
    patch.setClientForename("Race-Amended-Forename");

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    // 409 Conflict with the shared stable code and user-safe message.
    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    String body = result.getResponse().getContentAsString();
    assertThat(body).contains("CLAIM_VERSION_CONFLICT");
    assertThat(body).contains("The claim has changed since it was loaded");

    // The stale amendment saved nothing.
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID)).isEmpty();
    assertThat(claimRepository.findById(CLAIM_1_ID).orElseThrow().isAmended()).isFalse();
  }
}
