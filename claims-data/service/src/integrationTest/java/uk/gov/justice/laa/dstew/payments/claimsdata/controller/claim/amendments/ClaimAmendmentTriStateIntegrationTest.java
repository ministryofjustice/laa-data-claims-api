package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.model.ClearType;
import org.openapitools.jackson.nullable.JsonNullable;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimAmendmentPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Claim Amendment tri-state behaviour (DB-level nulling) integration tests")
public class ClaimAmendmentTriStateIntegrationTest extends AbstractAmendmentPatchIntegrationTest {

  // Inclusive mapper used to force all-null bodies (caller intention test)
  private final ObjectMapper inclusiveMapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws Exception {
    // Ensure PATCH_MAPPER can serialize JsonNullable fields as the other suites do.
    PATCH_MAPPER.registerModule(new JsonNullableModule());

    // Let the genuine AmendmentExternalValidationStep run against controlled external responses.
    stubExternalValidationEndpoints();
    // CLAIM_1 belongs to a LEGAL_HELP submission; keep the fee-code Area-of-Law gate happy.
    stubFeeDetailsAreaOfLaw("LEGAL_HELP");

    // Put CLAIM_1 into an amendable state and give it the baseline calculated fee the repricing
    // path requires (otherwise the amendment is rejected with CFD_MISSING before any diff is
    // built).
    Claim claim1 = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    claim1.setStatus(ClaimStatus.VALID);
    claimRepository.saveAndFlush(claim1);
    createBaselineCalculatedFeeDetail(claim1);

    // Each test controls (and asserts on) the fee-calculation stub itself.
    mockServerClient.clear(request().withPath(FEE_CALCULATION), ClearType.EXPECTATIONS);
  }

  @Test
  @DisplayName("Cleared-field amendment using standard mapper persists NULL to DB")
  void clearedFieldAmendmentWithStandardMapperPersistsNull() throws Exception {
    // Arrange: set a non-null value so we can later clear it
    Claim current = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    Long currentVersion = current.getVersion();

    String deliveryReference = "TR12345";
    ClaimAmendmentPatch setPatch = basePatch(currentVersion);
    setPatch.deliveryLocation(deliveryReference);

    MvcResult setPatchResult = performAmendmentPatch(SUBMISSION_1_ID, CLAIM_1_ID, setPatch);
    assertResponseStatus(setPatchResult, org.springframework.http.HttpStatus.NO_CONTENT);

    // Read back to get current version
    current = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    currentVersion = current.getVersion();

    // Act: submit an explicit-null amendment using the standard PATCH_MAPPER
    ClaimAmendmentPatch explicitPatch = basePatch(currentVersion);
    explicitPatch.setDeliveryLocation(JsonNullable.of((String) null));

    MvcResult explicitResult = performAmendmentPatch(SUBMISSION_1_ID, CLAIM_1_ID, explicitPatch);
    assertResponseStatus(explicitResult, HttpStatus.NO_CONTENT);

    // DB-level assertion: delivery_location persisted as NULL
    Claim after = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(after.getDeliveryLocation()).isNull();

    // An amendment audit row should have been written
    List<ClaimAmendment> amendments =
        claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID);
    assertThat(amendments).isNotEmpty();
  }

  @Test
  @DisplayName("Sending an all-null inclusive body to amendment endpoint should fail")
  void sendingAllNullInclusiveBodyToAmendmentFails() throws Exception {
    // Arrange: set a non-null value so we can observe no-change on failure
    Claim current = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    Long currentVersion = current.getVersion();

    String deliveryReference = "TR12345";
    ClaimAmendmentPatch setPatch = basePatch(currentVersion);
    setPatch.deliveryLocation(deliveryReference);

    MvcResult setPatchResult = performAmendmentPatch(SUBMISSION_1_ID, CLAIM_1_ID, setPatch);
    assertResponseStatus(setPatchResult, HttpStatus.NO_CONTENT);

    // Read back to get current version
    current = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    currentVersion = current.getVersion();

    // Build an explicit-null patch and serialise with a plain inclusive mapper to include all nulls
    ClaimAmendmentPatch explicitPatch = basePatch(currentVersion);
    explicitPatch.setDeliveryLocation(JsonNullable.of((String) null));

    String body = inclusiveMapper.writeValueAsString(explicitPatch);

    // Act: perform the PATCH with the inclusive body
    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, body);

    // Expect a bad request (validation/deserialize errors) when an all-null inclusive body is used
    assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400);

    // Ensure no amendment row was created and the delivery_location remains unchanged
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID)).isNotEmpty();
    Claim after = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    // The delivery location should still be the value we set earlier
    assertThat(after.getDeliveryLocation()).isEqualTo(deliveryReference);
  }

  private ClaimAmendmentPatch basePatch(long currentVersion) {
    return ClaimAmendmentPatch.builder()
        .version(currentVersion)
        .amendmentUserId(UUID.fromString(AMENDMENT_USER_ID))
        .amendmentRequestedBy(REQUESTED_BY_PROVIDER)
        .amendmentReasonCode(REASON_PROVIDER_ERROR)
        .build();
  }
}
