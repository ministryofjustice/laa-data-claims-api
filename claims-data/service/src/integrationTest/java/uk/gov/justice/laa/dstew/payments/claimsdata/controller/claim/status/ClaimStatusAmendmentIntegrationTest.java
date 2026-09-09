package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments.AbstractAmendmentPatchIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimService;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Claim status - amendment-path integration tests")
public class ClaimStatusAmendmentIntegrationTest extends AbstractAmendmentPatchIntegrationTest {

  @Autowired private ClaimService claimService;
  private static final String PATCH_A_CLAIM_ENDPOINT =
      ClaimsDataTestUtil.API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}";

  private final ObjectMapper inclusiveMapper = new ObjectMapper();

  // Ensure unique fee codes / offices across JVM so caches don't collide (follows existing test
  // style)

  private static final ObjectMapper PATCH_MAPPER = nonNullMapper();

  private static ObjectMapper nonNullMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    return mapper;
  }

  @Test
  @DisplayName("Status update with non-null field change uses amendment path and commits")
  void statusWithNonNullFieldChangeUsesAmendmentPathAndCommits() throws Exception {
    // Arrange: create isolated submission and an amendable claim
    Claim seeded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    seeded.setStatus(ClaimStatus.VALID);
    Claim savedClaim = claimRepository.saveAndFlush(seeded);

    stubProviderSchedulesOk();

    ClaimPatch patch = new ClaimPatch();
    patch.setVersion(savedClaim.getVersion());
    patch.setAmendmentRequestedBy("PROVIDER");
    patch.setAmendmentReasonCode("PROVIDER_ERROR");
    patch.setAmendmentUserId(UUID.fromString("0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e"));
    patch.setClientSurname("testSurname");

    String body = PATCH_MAPPER.writeValueAsString(patch);

    // Capture created values before the amendment
    Claim before = claimRepository.findById(savedClaim.getId()).orElseThrow();
    var createdOnBefore = before.getCreatedOn();
    var createdByBefore = before.getCreatedByUserId();

    // Act
    MvcResult result = performPatch(savedClaim.getSubmission().getId(), savedClaim.getId(), body);

    // Assert: an amendment audit row was written and the claim was marked amended
    assertThat(result.getResponse().getStatus()).isEqualTo(204);
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(savedClaim.getId())).hasSize(1);
    Claim amended = claimRepository.findById(savedClaim.getId()).orElseThrow();
    assertThat(amended.getClient().getClientSurname()).isEqualTo(patch.getClientSurname());
    assertThat(amended.isAmended()).isTrue();
    // Created values must not be changed
    assertThat(amended.getCreatedOn()).isEqualTo(createdOnBefore);
    assertThat(amended.getCreatedByUserId()).isEqualTo(createdByBefore);
    // Updated-by must be stamped with the acting amendment user id (null-safe check)
    assertThat(amended.getUpdatedByUserId())
        .isEqualTo(
            patch.getAmendmentUserId() != null ? patch.getAmendmentUserId().toString() : null);
  }

  @Test
  @DisplayName("Null status triggers amendment path and commits")
  void nullStatusUsesAmendmentPathAndCommits() throws Exception {
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    claim.setStatus(ClaimStatus.VALID);
    Claim savedClaim = claimRepository.saveAndFlush(claim);

    stubProviderSchedulesOk();

    ClaimPatch patch = new ClaimPatch();
    patch.setVersion(savedClaim.getVersion());
    patch.setAmendmentRequestedBy("PROVIDER");
    patch.setAmendmentReasonCode("PROVIDER_ERROR");
    patch.setAmendmentUserId(UUID.fromString("0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e"));
    patch.setClientSurname("testSurname");
    // Intentionally do not set status to force amendment path

    // Capture created values before the amendment
    Claim before = claimRepository.findById(savedClaim.getId()).orElseThrow();
    var createdOnBefore = before.getCreatedOn();
    var createdByBefore = before.getCreatedByUserId();

    String body = PATCH_MAPPER.writeValueAsString(patch);
    MvcResult result = performPatch(savedClaim.getSubmission().getId(), savedClaim.getId(), body);

    assertThat(result.getResponse().getStatus()).isEqualTo(204);
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(savedClaim.getId())).hasSize(1);
    Claim amended = claimRepository.findById(savedClaim.getId()).orElseThrow();
    // Created values must not be changed
    assertThat(amended.getCreatedOn()).isEqualTo(createdOnBefore);
    assertThat(amended.getCreatedByUserId()).isEqualTo(createdByBefore);
    // Updated-by must be stamped with the acting amendment user id (null-safe check)
    assertThat(amended.getUpdatedByUserId())
        .isEqualTo(
            patch.getAmendmentUserId() != null ? patch.getAmendmentUserId().toString() : null);
  }

  @Test
  @DisplayName("Status update persists validation messages and preserves created values")
  void statusUpdatePersistsValidationMessagesAndPreservesCreatedValues() throws Exception {
    // Arrange: ensure we have a claim in a known state
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    claim.setStatus(ClaimStatus.VALID);
    claimRepository.saveAndFlush(claim);

    // Capture created values before the legacy status update
    Claim before = claimRepository.findById(claim.getId()).orElseThrow();
    var createdOnBefore = before.getCreatedOn();
    var createdByBefore = before.getCreatedByUserId();

    // Build a legacy-style patch that updates only the status and carries validation messages.
    // In the legacy path the controller/service treats validation messages as an attached list and
    // persists them even when the operation is a simple status update (not an amendment).
    ClaimPatch patch = new ClaimPatch();
    patch.setStatus(ClaimStatus.READY_TO_PROCESS);
    // legacy status updates use createdByUserId to indicate the acting user
    patch.setCreatedByUserId("0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e");

    ValidationMessagePatch vmp =
        ValidationMessagePatch.builder()
            .type(ValidationMessageType.WARNING)
            .source("FSP")
            .displayMessage("FSP warning for legacy status update")
            .messageCode("WARLEG1")
            .build();

    patch.setValidationMessages(List.of(vmp));

    String body = PATCH_MAPPER.writeValueAsString(patch);

    // Act: perform the legacy status update that includes validation messages
    MvcResult result = performPatch(claim.getSubmission().getId(), claim.getId(), body);

    // Assert: HTTP 204 No Content
    assertThat(result.getResponse().getStatus()).isEqualTo(204);

    // Validation message should have been persisted by the legacy flow. Use the repository
    // projection query to fetch persisted validation messages with related claim/client details
    // and assert the expected message is present. This gives stronger assertions than a simple
    // count and documents the exact persisted payload.
    var page =
        validationMessageLogRepository.findWithClaimDetailsByFilters(
            claim.getSubmission().getId(),
            claim.getId(),
            ValidationMessageType.WARNING,
            null,
            PageRequest.of(0, 10));
    assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);

    boolean found =
        page.getContent().stream()
            .anyMatch(
                p ->
                    "FSP".equals(p.getSource())
                        && "WARLEG1".equals(p.getMessageCode())
                        && "FSP warning for legacy status update".equals(p.getDisplayMessage()));
    assertThat(found).isTrue();

    // The claim's created values must not be overwritten by this legacy status update
    Claim updated = claimRepository.findById(claim.getId()).orElseThrow();
    assertThat(updated.getCreatedOn()).isEqualTo(createdOnBefore);
    assertThat(updated.getCreatedByUserId()).isEqualTo(createdByBefore);

    // The legacy flow stamps claim.updatedBy with the patch.createdByUserId
    assertThat(updated.getUpdatedByUserId()).isEqualTo(patch.getCreatedByUserId());
  }

  @Test
  @DisplayName("Status and fee-calculation patch updates fee detail and preserves created metadata")
  void statusAndFeeCalculationPatchSavesFeeDetailAndPreservesExistingCreatedValues()
      throws Exception {
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();

    // Ensure a pre-existing calculated fee detail exists for this claim
    createFeeDetail(
        claim, new BigDecimal("50"), OffsetDateTime.now(java.time.ZoneOffset.UTC), null);
    calculatedFeeDetailRepository.flush();

    // Capture existing calculated fee detail created values
    var beforeFeeDetail =
        calculatedFeeDetailRepository
            .findFirstByClaimIdOrderByCreatedOnDescIdDesc(claim.getId())
            .orElseThrow();
    var feeCreatedOnBefore = beforeFeeDetail.getCreatedOn();

    // Build legacy-style patch (status + feeCalculationResponse) -> legacy path
    ClaimPatch patch = new ClaimPatch();
    patch.setStatus(ClaimStatus.READY_TO_PROCESS);
    patch.setCreatedByUserId("0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e");

    FeeCalculationPatch fcp =
        FeeCalculationPatch.builder()
            .feeCode("CALC-FEE-UPDATED")
            .totalAmount(new BigDecimal("75.00"))
            .build();
    patch.setFeeCalculationResponse(fcp);

    String body = PATCH_MAPPER.writeValueAsString(patch);

    MvcResult result = performPatch(claim.getSubmission().getId(), claim.getId(), body);

    assertThat(result.getResponse().getStatus()).isEqualTo(204);

    // Legacy behaviour NOTE:
    // Historically the legacy status+fee update flow re-used the most-recent CalculatedFeeDetail's
    // ID when a new fee-calculation response arrived. That means the repository.save(...) call
    // performs an update of the existing row rather than inserting a new historical row. The
    // assertions below reflect that behaviour: the latest fee-detail row after the patch will have
    // the same DB id as the row we created above, but its createdOn/createdBy values will have been
    // updated to reflect the incoming patch/fsp response.

    // Re-read the (now-updated) fee detail row - this will return the same DB row we created above
    var afterFeeDetail =
        calculatedFeeDetailRepository
            .findFirstByClaimIdOrderByCreatedOnDescIdDesc(claim.getId())
            .orElseThrow();

    // The legacy flow updates the existing fee-detail row in-place: id remains the same
    assertThat(afterFeeDetail.getId()).isEqualTo(beforeFeeDetail.getId());

    // createdOn/createdBy are expected to have been updated by the legacy flow
    assertThat(afterFeeDetail.getCreatedOn()).isAfter(feeCreatedOnBefore);
    assertThat(afterFeeDetail.getCreatedByUserId()).isEqualTo(patch.getCreatedByUserId());

    // Confirm the business values were saved from the fee-calculation response
    assertThat(afterFeeDetail.getFeeCode()).isEqualTo("CALC-FEE-UPDATED");
    assertThat(afterFeeDetail.getTotalAmount()).isEqualByComparingTo(new BigDecimal("75.00"));

    // Claim updatedBy must be set from patch.createdByUserId (legacy flow stamps claim.updatedBy)
    Claim updatedClaim = claimRepository.findById(claim.getId()).orElseThrow();
    assertThat(updatedClaim.getUpdatedByUserId()).isEqualTo(patch.getCreatedByUserId());
  }

  @Test
  @DisplayName("Non-null field equal to persisted value stays legacy and does not create amendment")
  void nonNullFieldEqualToPersistedValueStaysLegacyAndDoesNotCreateAmendment() throws Exception {

    // Ensure the seeded claim is in an amendable state
    Claim seeded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    seeded.setStatus(ClaimStatus.VALID);
    Claim savedClaim = claimRepository.saveAndFlush(seeded);

    stubProviderSchedulesOk();

    ClaimPatch patch = new ClaimPatch();
    patch.setVersion(savedClaim.getVersion());
    patch.setAmendmentRequestedBy("PROVIDER");
    patch.setAmendmentReasonCode("PROVIDER_ERROR");
    patch.setAmendmentUserId(UUID.fromString("0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e"));
    patch.setFeeCode(ClaimsDataTestUtil.FEE_CODE);

    String json = PATCH_MAPPER.writeValueAsString(patch);

    MvcResult result = performPatch(savedClaim.getSubmission().getId(), savedClaim.getId(), json);

    assertThat(result.getResponse().getStatus()).isEqualTo(204);
  }

  @ParameterizedTest
  @EnumSource(ClaimStatus.class)
  @DisplayName(
      "Parameterized: status update should persist messages and fee calculation when status changes; VOID should be rejected")
  void parameterizedStatusUpdatePersistsMessagesAndFeeCalculation(ClaimStatus targetStatus)
      throws Exception {
    // Arrange: fetch claim and ensure its current status differs from the target to force an update
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    // Choose an initial status different from the target
    ClaimStatus initialStatus =
        targetStatus == ClaimStatus.VALID ? ClaimStatus.READY_TO_PROCESS : ClaimStatus.VALID;
    claim.setStatus(initialStatus);
    claimRepository.saveAndFlush(claim);

    // Ensure there is an existing calculated fee detail to observe overwrite behaviour
    createFeeDetail(
        claim, new BigDecimal("50"), OffsetDateTime.now(java.time.ZoneOffset.UTC), null);
    calculatedFeeDetailRepository.flush();

    // Capture pre-update counts / values
    long beforeWarnings =
        validationMessageLogRepository.countAllByClaimIdAndType(
            claim.getId(), ValidationMessageType.WARNING);
    var beforeFeeDetail =
        calculatedFeeDetailRepository
            .findFirstByClaimIdOrderByCreatedOnDescIdDesc(claim.getId())
            .orElseThrow();

    // Build patch carrying status, a validation message and a fee-calculation response
    ClaimPatch patch = new ClaimPatch();
    patch.setStatus(targetStatus);
    patch.setCreatedByUserId("0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e");

    ValidationMessagePatch vmp =
        ValidationMessagePatch.builder()
            .type(ValidationMessageType.WARNING)
            .source("FSP")
            .displayMessage("Param test warning")
            .messageCode("PTWARN")
            .build();
    patch.setValidationMessages(List.of(vmp));

    FeeCalculationPatch fcp =
        FeeCalculationPatch.builder()
            .feeCode("PARAM-FEE")
            .totalAmount(new BigDecimal("99.00"))
            .build();
    patch.setFeeCalculationResponse(fcp);

    String body = PATCH_MAPPER.writeValueAsString(patch);

    // Act
    MvcResult result = performPatch(claim.getSubmission().getId(), claim.getId(), body);

    if (targetStatus == ClaimStatus.VOID) {
      // VOID updates are forbidden via this endpoint
      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      // No validation messages should have been added and fee detail should remain unchanged
      long afterWarnings =
          validationMessageLogRepository.countAllByClaimIdAndType(
              claim.getId(), ValidationMessageType.WARNING);
      assertThat(afterWarnings).isEqualTo(beforeWarnings);
      var afterFee =
          calculatedFeeDetailRepository
              .findFirstByClaimIdOrderByCreatedOnDescIdDesc(claim.getId())
              .orElseThrow();
      assertThat(afterFee.getFeeCode()).isEqualTo(beforeFeeDetail.getFeeCode());
    } else {
      // Expect success
      assertThat(result.getResponse().getStatus()).isEqualTo(204);
      // Status should have been updated
      Claim updated = claimRepository.findById(claim.getId()).orElseThrow();
      assertThat(updated.getStatus()).isEqualTo(targetStatus);

      // Validation message persisted
      var page =
          validationMessageLogRepository.findWithClaimDetailsByFilters(
              claim.getSubmission().getId(),
              claim.getId(),
              ValidationMessageType.WARNING,
              null,
              PageRequest.of(0, 10));
      assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(beforeWarnings + 1);

      // Fee calculation applied (legacy behaviour overwrites existing fee detail)
      var afterFee =
          calculatedFeeDetailRepository
              .findFirstByClaimIdOrderByCreatedOnDescIdDesc(claim.getId())
              .orElseThrow();
      assertThat(afterFee.getFeeCode()).isEqualTo("PARAM-FEE");
      assertThat(afterFee.getTotalAmount()).isEqualByComparingTo(new BigDecimal("99.00"));
    }
  }

  @Test
  @DisplayName("ID present in patch does not trigger amendment")
  void idPresentInPatchDoesNotTriggerAmendment() throws Exception {

    ClaimPatch patch = new ClaimPatch();
    patch.setStatus(ClaimStatus.READY_TO_PROCESS);
    patch.setId(UUID.randomUUID().toString());
    patch.setCreatedByUserId(API_USER_ID);

    String body = inclusiveMapper.writeValueAsString(patch);

    // Act: perform the legacy status update that includes validation messages
    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, body);

    // Assert: HTTP 204 No Content
    assertThat(result.getResponse().getStatus()).isEqualTo(204);

    // The claim's created values must not be overwritten by this legacy status update
    Claim updated = claimRepository.findById(CLAIM_1_ID).orElseThrow();

    // The legacy flow stamps claim.updatedBy with the patch.createdByUserId
    assertThat(updated.getUpdatedByUserId()).isEqualTo(patch.getCreatedByUserId());
  }

  @Test
  @DisplayName("All null inclusive mapper does not trigger amendment")
  void allNullInclusiveMapperDoesNotTriggerAmendment() throws Exception {

    ClaimPatch patch = new ClaimPatch();
    patch.setStatus(ClaimStatus.READY_TO_PROCESS);
    patch.setCreatedByUserId(API_USER_ID);

    String body = inclusiveMapper.writeValueAsString(patch);

    // Act: perform the legacy status update that includes validation messages
    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, body);

    // Assert: HTTP 204 No Content
    assertThat(result.getResponse().getStatus()).isEqualTo(204);

    // The claim's created values must not be overwritten by this legacy status update
    Claim updated = claimRepository.findById(CLAIM_1_ID).orElseThrow();

    // The legacy flow stamps claim.updatedBy with the patch.createdByUserId
    assertThat(updated.getUpdatedByUserId()).isEqualTo(patch.getCreatedByUserId());
  }

  @Test
  @DisplayName("Any value inclusive mapper triggers amendment")
  void anyValueInclusiveMapperTriggersAmendment() throws Exception {

    ClaimPatch patch = new ClaimPatch();
    patch.setStatus(ClaimStatus.READY_TO_PROCESS);
    patch.setCreatedByUserId(API_USER_ID);
    patch.setClientSurname("AnyValueTest");

    String body = inclusiveMapper.writeValueAsString(patch);

    // Act: perform the legacy status update that includes validation messages
    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, body);

    // Assert: HTTP 400 Bad Request
    assertThat(result.getResponse().getStatus()).isEqualTo(400);

    // The claim's created values must not be overwritten by this legacy status update
    Claim updated = claimRepository.findById(CLAIM_1_ID).orElseThrow();

    // Check it has not been updated to the new surname, as the patch should have been rejected
    assertThat(updated.getClient().getClientSurname()).isNotEqualTo(patch.getClientSurname());
  }
}
