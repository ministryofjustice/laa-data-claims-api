package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.REASON_PROVIDER_ERROR;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.REQUESTED_BY_PROVIDER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.VALID_USER_UUID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ValidationMessageLogFixtures.FSP_SOURCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ValidationMessageLogFixtures.currentFspWarning;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_2_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.PageRequest;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationIssue;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationSeverity;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ValidationMessageWithClaimDetailsProjection;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentExternalValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;

/**
 * End-to-end pipeline contract for the FSP warning lifecycle: covers the observable outcomes of a
 * successful/failed amendment on the {@code validation_message_log} rows via the real orchestrated
 * flow.
 *
 * <p>The sibling suite {@code ClaimAmendmentValidationMessagePersistenceIntegrationTest} pins the
 * narrower persistence-service contract (managed-claim versioning, severity filtering, FSP-context
 * guard); this suite proves the same rules hold once the whole pipeline runs.
 */
@DisplayName("Claim amendment warning lifecycle integration test")
class ClaimAmendmentWarningLifecycleIntegrationTest
    extends AbstractAmendmentPipelineIntegrationTest {

  @Test
  @DisplayName(
      "successful repricing persists returned warnings, supersedes obsolete ones, and keeps read counts consistent")
  void successfulRepricingPersistsWarningsAndKeepsReadsConsistent() throws IOException {
    seedClaimsData();
    Claim claim1 = updateClaimStatus(CLAIM_1_ID, ClaimStatus.VALID);
    Claim claim2 = claimRepository.findById(CLAIM_2_ID).orElseThrow();
    validationMessageLogRepository.saveAll(
        List.of(
            currentFspWarning(SUBMISSION_1_ID, CLAIM_1_ID, claim1.getVersion(), "obsolete-warning"),
            currentFspWarning(
                SUBMISSION_1_ID, CLAIM_2_ID, claim2.getVersion(), "other-claim-warning")));
    stubExternalValidationEndpoints();

    ClaimAmendmentValidationStep warningStep =
        state -> {
          state.addWarnings(List.of(warning("WAR-001", "fresh-fsp-warning")));
          return List.of();
        };

    ClaimAmendmentService service =
        amendmentPipeline().replaceStep(AmendmentExternalValidationStep.class, warningStep).build();

    ClaimAmendmentResult result = submitInNewTransaction(service, CLAIM_1_ID, pricingPayload());

    assertThat(result.isSuccess()).isTrue();
    long seedClaimVersion = claim1.getVersion();
    long committedClaimVersion = claimRepository.findById(CLAIM_1_ID).orElseThrow().getVersion();
    assertThat(committedClaimVersion).isGreaterThan(seedClaimVersion);
    assertThat(
            validationMessageLogRepository.countAllByClaimIdAndType(
                CLAIM_1_ID, ValidationMessageType.WARNING))
        .isEqualTo(1);
    assertThat(
            validationMessageLogRepository.countAllByClaimIdAndType(
                CLAIM_2_ID, ValidationMessageType.WARNING))
        .isEqualTo(1);
    assertThat(
            validationMessageLogRepository.countDistinctClaimIdsBySubmissionIdAndType(
                SUBMISSION_1_ID, ValidationMessageType.WARNING))
        .isEqualTo(2);

    List<ValidationMessageLog> claim1Warnings = warningLogsForClaim(CLAIM_1_ID);
    assertThat(claim1Warnings).hasSize(2);
    ValidationMessageLog freshWarning =
        claim1Warnings.stream()
            .filter(log -> log.getDisplayMessage().equals("fresh-fsp-warning"))
            .findFirst()
            .orElseThrow();
    assertThat(freshWarning.getSupersededByVersion()).isZero();
    assertThat(freshWarning.getVersion()).isEqualTo(committedClaimVersion);
    assertThat(claim1Warnings)
        .filteredOn(log -> log.getDisplayMessage().equals("obsolete-warning"))
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.getSupersededByVersion()).isEqualTo(committedClaimVersion);
              assertThat(log.getVersion()).isEqualTo(seedClaimVersion);
            });

    List<String> normalReadMessages =
        validationMessageLogRepository
            .findWithClaimDetailsByFilters(
                SUBMISSION_1_ID,
                CLAIM_1_ID,
                ValidationMessageType.WARNING,
                FSP_SOURCE,
                PageRequest.of(0, 10))
            .getContent()
            .stream()
            .map(ValidationMessageWithClaimDetailsProjection::getDisplayMessage)
            .toList();
    assertThat(normalReadMessages).containsExactly("fresh-fsp-warning");

    assertThat(warningLogsForClaim(CLAIM_2_ID))
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.getDisplayMessage()).isEqualTo("other-claim-warning");
              assertThat(log.getSupersededByVersion()).isZero();
            });
  }

  @Test
  @DisplayName("successful non-pricing amendment leaves current FSP warnings unchanged")
  void nonPricingAmendmentLeavesWarningsUnchanged() throws IOException {
    seedAssessmentsData();
    Claim claim = updateClaimStatus(CLAIM_1_ID, ClaimStatus.VALID);
    ValidationMessageLog seededWarning =
        currentFspWarning(SUBMISSION_1_ID, CLAIM_1_ID, claim.getVersion(), "keep-current-warning");
    validationMessageLogRepository.save(seededWarning);
    stubExternalValidationEndpoints();

    ClaimAmendmentResult result =
        submitInNewTransaction(amendmentPipeline().build(), CLAIM_1_ID, nonPricingPayload());

    assertThat(result.isSuccess()).isTrue();
    Claim reloadedClaim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(reloadedClaim.getVersion()).isGreaterThan(claim.getVersion());
    assertThat(warningLogsForClaim(CLAIM_1_ID))
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.getId()).isEqualTo(seededWarning.getId());
              assertThat(log.getDisplayMessage()).isEqualTo("keep-current-warning");
              assertThat(log.getVersion()).isEqualTo(claim.getVersion());
              assertThat(log.getSupersededByVersion()).isZero();
            });
  }

  @Test
  @DisplayName(
      "successful repricing with no returned warnings clears current warnings for the amended claim only")
  void repricingWithNoWarningsLeavesNoCurrentWarningsForAmendedClaimOnly() throws IOException {
    seedClaimsData();
    Claim claim1 = updateClaimStatus(CLAIM_1_ID, ClaimStatus.VALID);
    Claim claim2 = claimRepository.findById(CLAIM_2_ID).orElseThrow();
    validationMessageLogRepository.saveAll(
        List.of(
            currentFspWarning(
                SUBMISSION_1_ID, CLAIM_1_ID, claim1.getVersion(), "claim1-current-warning"),
            currentFspWarning(
                SUBMISSION_1_ID, CLAIM_2_ID, claim2.getVersion(), "claim2-current-warning")));
    stubExternalValidationEndpoints();

    ClaimAmendmentValidationStep noWarningStep = state -> List.of();
    ClaimAmendmentService service =
        amendmentPipeline()
            .replaceStep(AmendmentExternalValidationStep.class, noWarningStep)
            .build();

    ClaimAmendmentResult result = submitInNewTransaction(service, CLAIM_1_ID, pricingPayload());

    assertThat(result.isSuccess()).isTrue();
    assertThat(
            validationMessageLogRepository.countAllByClaimIdAndType(
                CLAIM_1_ID, ValidationMessageType.WARNING))
        .isZero();
    assertThat(
            validationMessageLogRepository.countAllByClaimIdAndType(
                CLAIM_2_ID, ValidationMessageType.WARNING))
        .isEqualTo(1);

    assertThat(warningLogsForClaim(CLAIM_1_ID))
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.getDisplayMessage()).isEqualTo("claim1-current-warning");
              assertThat(log.getSupersededByVersion()).isNotZero();
            });
    assertThat(warningLogsForClaim(CLAIM_2_ID))
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.getDisplayMessage()).isEqualTo("claim2-current-warning");
              assertThat(log.getSupersededByVersion()).isZero();
            });
    assertThat(
            validationMessageLogRepository
                .findWithClaimDetailsByFilters(
                    SUBMISSION_1_ID,
                    CLAIM_1_ID,
                    ValidationMessageType.WARNING,
                    FSP_SOURCE,
                    PageRequest.of(0, 10))
                .getContent())
        .isEmpty();
  }

  @Test
  @DisplayName("rejected amendment leaves saved warning state and the claim unchanged")
  void failedAmendmentDoesNotChangeWarnings() throws IOException {
    seedClaimsData();
    Claim claim = updateClaimStatus(CLAIM_1_ID, ClaimStatus.VALID);
    long versionBefore = claim.getVersion();
    ValidationMessageLog seededWarning =
        currentFspWarning(SUBMISSION_1_ID, CLAIM_1_ID, versionBefore, "existing-warning");
    validationMessageLogRepository.save(seededWarning);
    stubExternalValidationEndpoints();

    ClaimAmendmentResult result =
        submitInNewTransaction(amendmentPipeline().build(), CLAIM_1_ID, invalidPayload());

    assertThat(result.isSuccess()).isFalse();
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID)).isEmpty();
    Claim reloadedClaim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(reloadedClaim.getVersion()).isEqualTo(versionBefore);
    assertThat(reloadedClaim.isAmended()).isFalse();
    assertThat(warningLogsForClaim(CLAIM_1_ID))
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.getId()).isEqualTo(seededWarning.getId());
              assertThat(log.getSupersededByVersion()).isZero();
              assertThat(log.getVersion()).isEqualTo(versionBefore);
            });
  }

  @Test
  @DisplayName("voided amendment is rejected and leaves current warnings and the claim unchanged")
  void voidedAmendmentLeavesWarningsUnchanged() {
    seedClaimsData();
    Claim claim = updateClaimStatus(CLAIM_1_ID, ClaimStatus.VOID);
    long versionBefore = claim.getVersion();
    ValidationMessageLog seededWarning =
        currentFspWarning(SUBMISSION_1_ID, CLAIM_1_ID, versionBefore, "void-claim-warning");
    validationMessageLogRepository.save(seededWarning);

    ClaimAmendmentResult result =
        submitInNewTransaction(amendmentPipeline().build(), CLAIM_1_ID, nonPricingPayload());

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.errors())
        .extracting(ClaimAmendmentValidationError::getCode)
        .containsExactly(
            ClaimAmendmentValidationCode.INVALID_VOIDED_CLAIM_NOT_AMENDABLE.toString());
    Claim reloadedClaim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(reloadedClaim.getVersion()).isEqualTo(versionBefore);
    assertThat(reloadedClaim.isAmended()).isFalse();
    assertThat(warningLogsForClaim(CLAIM_1_ID))
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.getId()).isEqualTo(seededWarning.getId());
              assertThat(log.getSupersededByVersion()).isZero();
              assertThat(log.getVersion()).isEqualTo(versionBefore);
            });
  }

  private Claim updateClaimStatus(UUID claimId, ClaimStatus status) {
    Claim claim = claimRepository.findById(claimId).orElseThrow();
    claim.setStatus(status);
    return claimRepository.saveAndFlush(claim);
  }

  private ClaimAmendmentPayload pricingPayload() {
    return ClaimAmendmentPayload.builder()
        .amendmentRequestedBy(JsonNullable.of(REQUESTED_BY_PROVIDER))
        .amendmentReasonCode(JsonNullable.of(REASON_PROVIDER_ERROR))
        .amendmentUserId(JsonNullable.of(VALID_USER_UUID))
        .netProfitCostsAmount(JsonNullable.of(BigDecimal.valueOf(200)))
        .build();
  }

  private ClaimAmendmentPayload nonPricingPayload() {
    return ClaimAmendmentPayload.builder()
        .amendmentRequestedBy(JsonNullable.of(REQUESTED_BY_PROVIDER))
        .amendmentReasonCode(JsonNullable.of(REASON_PROVIDER_ERROR))
        .amendmentUserId(JsonNullable.of(VALID_USER_UUID))
        .clientSurname(JsonNullable.of("UpdatedSurname"))
        .build();
  }

  private ClaimAmendmentPayload invalidPayload() {
    return ClaimAmendmentPayload.builder()
        .amendmentUserId(JsonNullable.of("not-a-uuid"))
        .feeCode(JsonNullable.of("AMEDFEECOD"))
        .build();
  }

  private ValidationIssue warning(String code, String message) {
    return ValidationIssue.builder()
        .code(code)
        .message(message)
        .severity(ValidationSeverity.WARNING)
        .build();
  }

  private List<ValidationMessageLog> warningLogsForClaim(UUID claimId) {
    return validationMessageLogRepository.findAll().stream()
        .filter(log -> claimId.equals(log.getClaimId()))
        .filter(log -> log.getType() == ValidationMessageType.WARNING)
        .filter(log -> FSP_SOURCE.equals(log.getSource()))
        .toList();
  }
}
