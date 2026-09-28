package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments.AbstractAmendmentPatchIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

@TestPropertySource(properties = "laa.claims.api.validated-pending-approval.enabled=true")
@DisplayName("Claim validated-pending-approval lifecycle integration tests")
class ClaimStatusIntermediateIntegrationTest extends AbstractAmendmentPatchIntegrationTest {

  @Test
  @DisplayName(
      "With the lifecycle enabled, updating a claim to VALIDATED_PENDING_APPROVAL persists the "
          + "intermediate status")
  void claimStatusPreservesIntermediateStatusWhenLifecycleEnabled() throws Exception {
    stubProviderSchedulesOk();
    UUID submissionId = createSubmissionWithUniqueOffice();
    Claim claim = createAmendableClaim(submissionId, ignored -> {});

    ClaimPatch patch = new ClaimPatch();
    patch.setStatus(ClaimStatus.VALIDATED_PENDING_APPROVAL);
    patch.setCreatedByUserId(AMENDMENT_USER_ID);

    MvcResult result = performPatch(submissionId, claim.getId(), patch);

    assertThat(result.getResponse().getStatus()).isEqualTo(204);
    assertThat(claimRepository.findById(claim.getId()).orElseThrow().getStatus())
        .isEqualTo(ClaimStatus.VALIDATED_PENDING_APPROVAL);
  }
}
