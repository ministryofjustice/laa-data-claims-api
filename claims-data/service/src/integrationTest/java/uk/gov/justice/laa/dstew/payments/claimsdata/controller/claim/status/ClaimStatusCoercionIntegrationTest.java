package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

@TestPropertySource(properties = "laa.claims.api.features.validated-pending-approval=false")
@DisplayName("Claim status coercion integration tests")
class ClaimStatusCoercionIntegrationTest extends AbstractIntegrationTest {

  private static final String PATCH_A_CLAIM_ENDPOINT =
      API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}";

  @BeforeEach
  void setUp() {
    seedClaimsData();
  }

  @Test
  @DisplayName(
      "With the VALIDATED_PENDING_APPROVAL lifecycle disabled, updating a claim to VALIDATED_PENDING_APPROVAL persists VALID")
  void shouldCoerceValidatedPendingApprovalStatusWhenLifecycleDisabled() throws Exception {
    ClaimPatch patch = new ClaimPatch();
    patch.setStatus(ClaimStatus.VALIDATED_PENDING_APPROVAL);

    mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, SUBMISSION_1_ID, CLAIM_1_ID)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent());

    assertThat(claimRepository.findById(CLAIM_1_ID).orElseThrow().getStatus())
        .isEqualTo(ClaimStatus.VALID);
  }
}
