package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getAssessmentPost;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.VoidClaimRequest;

/**
 * Proves that {@code Assessment} rows inserted via {@code POST /claims/{claimId}/assessments} and
 * {@code POST /claims/{claimId}/void} carry consistent, correctly attributed audit fields, and that
 * the parent {@code Claim} row is stamped with the same acting user.
 *
 * <p>Specifically, for both endpoints:
 *
 * <ul>
 *   <li>{@code Assessment.updatedByUserId} equals {@code Assessment.createdByUserId} (the user who
 *       raised the request) and both {@code createdOn}/{@code updatedOn} are populated (Hibernate
 *       stamps them at insert time, so they are equal within microsecond tolerance);
 *   <li>the parent {@code Claim.updatedByUserId} is also stamped with that same acting user, and
 *       {@code Claim.updatedOn} is refreshed past its prior value.
 * </ul>
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("Assessment and claim audit-field attribution integration test")
class AssessmentAndClaimAuditFieldsIntegrationTest extends AbstractAmendmentPatchIntegrationTest {

  @Test
  @DisplayName("Assessment creation with timestampable fields on the assessment and claim")
  void assessmentCreationTimestampableFieldsOnAssessmentAndClaim() throws Exception {
    Claim seeded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    seeded.setStatus(ClaimStatus.VALID);
    claimRepository.saveAndFlush(seeded);

    var claimUpdatedOnBeforeAssessment =
        claimRepository.findById(CLAIM_1_ID).orElseThrow().getUpdatedOn();

    var request = getAssessmentPost();

    MvcResult result = mockMvc.perform(
              post(API_URI_PREFIX + "/claims/{claimId}/assessments", CLAIM_1_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    UUID assessmentId = UUID.fromString(
            objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

    Assessment assessment = assessmentRepository.findById(assessmentId).orElseThrow();
    assertThat(assessment.getCreatedByUserId()).isEqualTo(request.getCreatedByUserId());
    assertThat(assessment.getUpdatedByUserId()).isEqualTo(assessment.getCreatedByUserId());
    assertThat(assessment.getCreatedOn()).isNotNull();
    assertThat(assessment.getUpdatedOn()).isNotNull().isAfterOrEqualTo(assessment.getCreatedOn());

    Claim claimAfter = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(claimAfter.getUpdatedByUserId()).isEqualTo(request.getCreatedByUserId());
    assertThat(claimAfter.getUpdatedOn()).isAfter(claimUpdatedOnBeforeAssessment);
  }

  @Test
  @DisplayName("void claim timestampable fields on the assessment and claim")
  void voidClaim_stampsMatchingAuditFieldsOnAssessmentAndClaim() throws Exception {
    Claim seeded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    seeded.setStatus(ClaimStatus.VALID);
    Claim amendable = claimRepository.saveAndFlush(seeded);
    var claimUpdatedOnBeforeVoid = amendable.getUpdatedOn();

    UUID voidingUserId = UUID.fromString("01a0f2be-cd8a-73e4-ad41-a4244e4bd889");
    var voidRequest =
        new VoidClaimRequest()
            .createdByUserId(voidingUserId)
            .assessmentReason("test void reason")
            .version(amendable.getVersion());

    MvcResult result = mockMvc.perform(
            post(API_URI_PREFIX + "/claims/{claimId}/void", CLAIM_1_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(voidRequest)))
            .andExpect(status().isCreated())
            .andReturn();

    UUID assessmentId = UUID.fromString(
            objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

    Assessment assessment = assessmentRepository.findById(assessmentId).orElseThrow();
    assertThat(assessment.getCreatedByUserId()).isEqualTo(voidingUserId.toString());
    assertThat(assessment.getUpdatedByUserId()).isEqualTo(assessment.getCreatedByUserId());
    assertThat(assessment.getCreatedOn()).isNotNull();
    assertThat(assessment.getUpdatedOn()).isNotNull().isAfterOrEqualTo(assessment.getCreatedOn());

    Claim claimAfter = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(claimAfter.getStatus()).isEqualTo(ClaimStatus.VOID);
    assertThat(claimAfter.getUpdatedByUserId()).isEqualTo(voidingUserId.toString());
    assertThat(claimAfter.getUpdatedOn()).isAfter(claimUpdatedOnBeforeVoid);
  }
}
