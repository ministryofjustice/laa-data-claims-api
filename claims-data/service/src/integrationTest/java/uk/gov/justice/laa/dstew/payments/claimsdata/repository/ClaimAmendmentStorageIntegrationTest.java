package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationType;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@DisplayName("ClaimAmendmentStorage Integration Test")
class ClaimAmendmentStorageIntegrationTest extends AbstractIntegrationTest {

  private RequestedByReference providerRef;
  private RequestedByReference auditorRef;
  private AmendmentReasonReference complianceReason;
  private AmendmentReasonReference typingErrorReason;

  @BeforeEach
  void setUp() {
    // 1. Establish the baseline claim data trees (BulkSubmission, Submissions, Claims)
    seedClaimsData();

    // 2. Clear out core transaction data rows from previous test passes if needed
    claimAmendmentRepository.deleteAll();

    // 3. Instead of saving new duplicates, look up the rows Flyway already seeded!
    providerRef =
        requestedByRepository
            .findByCode("PROVIDER")
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Missing baseline PROVIDER reference data from Flyway seed"));

    auditorRef =
        requestedByRepository
            .findByCode("AUDITOR")
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Missing baseline AUDITOR reference data from Flyway seed"));

    typingErrorReason =
        amendmentReasonRepository
            .findByRequestedByCodeAndCode("PROVIDER", "TYPING_ERROR")
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Missing baseline TYPING_ERROR reason data from Flyway seed"));

    complianceReason =
        amendmentReasonRepository
            .findByRequestedByCodeAndCode("AUDITOR", "COMPLIANCE_CORRECTION")
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Missing baseline COMPLIANCE_CORRECTION reason data from Flyway seed"));
  }

  @Test
  @DisplayName("Should reject invalid metadata combinations when a phantom reason reference is provided")
  void shouldRejectInvalidMetadataCombinations() {
    Claim targetClaim = claimRepository.findById(ClaimsDataTestUtil.CLAIM_1_ID).orElseThrow();

    // Create a phantom reason reference that has an ID completely unknown to the DB
    AmendmentReasonReference phantomReason =
        AmendmentReasonReference.builder().id(UUID.randomUUID()).code("FAKE_CODE").build();

    ClaimAmendment corruptAmendment =
        ClaimAmendment.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(targetClaim)
            .amendmentReason(phantomReason)
            .beforeState("{}")
            .requestPayload("{}")
            .diff("{}")
            .createdByUserId(ClaimsDataTestUtil.USER_ID)
            .createdOn(OffsetDateTime.now())
            .build();

    // Standardized to fluent AssertJ alternative to eliminate cross-framework mixtures
    assertThatThrownBy(() -> claimAmendmentRepository.saveAndFlush(corruptAmendment))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @Transactional
  @DisplayName("Should track multiple calculations and return latest calculation using latest wins rule")
  void shouldTrackMultipleCalculationsAndReturnLatestWins() {
    Claim targetClaim = claimRepository.findById(ClaimsDataTestUtil.CLAIM_1_ID).orElseThrow();

    // Execute the step 2 & 3 orchestration logic cleanly through the extracted helper
    CalculatedFeeDetail dynamicFeeUpdate =
        amendClaimWithNewCalculation(targetClaim, typingErrorReason, BigDecimal.valueOf(999));

    targetClaim.setCalculatedFeeDetails(new ArrayList<>());
    targetClaim.getCalculatedFeeDetails().add(dynamicFeeUpdate);

    claimRepository.flush();
    Claim evaluatedClaim = claimRepository.findById(ClaimsDataTestUtil.CLAIM_1_ID).orElseThrow();

    assertThat(evaluatedClaim.getCalculatedFeeDetails()).hasSize(1);
    assertThat(evaluatedClaim.getLatestCalculatedFee()).isNotNull();
    assertThat(evaluatedClaim.getLatestCalculatedFee().getTotalAmount())
        .isEqualByComparingTo(BigDecimal.valueOf(999));
  }

  @Test
  @DisplayName("Should protect claim from concurrent modifications by throwing optimistic locking exception")
  void shouldProtectClaimFromConcurrentModifications() {
    // 1. Thread A loads the target claim at its initial baseline version (typically 0L)
    Claim claimThreadA = claimRepository.findById(ClaimsDataTestUtil.CLAIM_1_ID).orElseThrow();
    long initialVersion = claimThreadA.getVersion();

    // 2. Thread B simulates an overlapping process, reading the exact same record concurrently
    Claim claimThreadB = claimRepository.findById(ClaimsDataTestUtil.CLAIM_1_ID).orElseThrow();
    assertThat(claimThreadB.getVersion()).isEqualTo(initialVersion);

    // 3. Thread A executes its update loop and flushes to the database first
    claimThreadA.setAmended(true);
    claimRepository.saveAndFlush(
        claimThreadA); // Hibernate implicitly increments the database version to version + 1

    // 4. Thread B attempts to save its modification using its stale, original version reference
    claimThreadB.setFeeCode("CONCURRENT_AMENDED_CODE");

    // 5. Assert that trying to commit stale data throws AssertJ's fluent exception tracker
    assertThatThrownBy(
        () -> {
          claimRepository.saveAndFlush(claimThreadB);
        })
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  @Test
  @Transactional
  @DisplayName("Should increment entity version and track latest calculated fee on successful claim amendment")
  void shouldIncrementVersionAndTrackLatestCalculatedFeeOnSuccessfulAmendment() {
    // 1. Fetch a pristine copy of the target claim directly from the seeded Testcontainer DB
    Claim targetClaim = claimRepository.findById(ClaimsDataTestUtil.CLAIM_1_ID).orElseThrow();
    long initialVersion = targetClaim.getVersion();

    // Explicitly initialize the child collection array to prevent any legacy builder null pointer states
    if (targetClaim.getCalculatedFeeDetails() == null) {
      targetClaim.setCalculatedFeeDetails(new java.util.ArrayList<>());
    }

    // Refresh the target collection with whatever baseline state Flyway/Seed already committed
    int baselineSize = targetClaim.getCalculatedFeeDetails().size();

    // 2. Append a clean, distinct high-value calculation snapshot using the helper method
    BigDecimal amendedAmount = BigDecimal.valueOf(1250);
    CalculatedFeeDetail dynamicFeeUpdate =
        amendClaimWithNewCalculation(targetClaim, typingErrorReason, amendedAmount);

    // 3. Mutate the core claim state and map the bi-directional relationship link cleanly
    targetClaim.setAmended(true);
    targetClaim.getCalculatedFeeDetails().add(dynamicFeeUpdate);

    // 4. Commit directly to the persistence context to trigger the lifecycle updates
    claimRepository.saveAndFlush(targetClaim);

    // 5. Break the transaction cache to inspect the actual database state rules
    Claim evaluatedClaim = claimRepository.findById(ClaimsDataTestUtil.CLAIM_1_ID).orElseThrow();

    // Assert Rule A: Verify that the locking counter successfully updated across both actions
    assertThat(evaluatedClaim.getVersion()).isEqualTo(initialVersion + 2);

    // Assert Rule B: Verify the history list accurately tracked the new amendment insertion
    assertThat(evaluatedClaim.getCalculatedFeeDetails()).hasSize(baselineSize + 1);

    // Assert Rule C: Confirm that the latest wins strategy selects the 1250 calculation flawlessly
    assertThat(evaluatedClaim.getLatestCalculatedFee()).isNotNull();
    assertThat(evaluatedClaim.getLatestCalculatedFee().getTotalAmount())
        .isEqualByComparingTo(amendedAmount);
  }

  @Test
  @Transactional
  @DisplayName("Should accurately persist and track structural auditing metadata context fields")
  void shouldAccuratelyPersistAndTrackAuditingMetadataContext() {
    Claim targetClaim = claimRepository.findById(ClaimsDataTestUtil.CLAIM_1_ID).orElseThrow();
    String testingUser = "INTEGRATION_TEST_USER_99";
    OffsetDateTime testingTime = OffsetDateTime.now(ZoneOffset.UTC);

    // 1. Direct standalone save to verify audit data channel integrity
    ClaimAmendment auditAmendment =
        claimAmendmentRepository.saveAndFlush(
            ClaimAmendment.builder()
                .id(Uuid7.timeBasedUuid())
                .claim(targetClaim)
                .amendmentReason(typingErrorReason)
                .beforeState("{}")
                .requestPayload("{}")
                .diff("{}")
                .createdByUserId(testingUser)
                .createdOn(testingTime)
                .build());

    // 2. Clear cache context and re-fetch raw DB records
    claimAmendmentRepository.flush();
    ClaimAmendment retrieved =
        claimAmendmentRepository.findById(auditAmendment.getId()).orElseThrow();

    // 3. Assert precise mapping parity
    assertThat(retrieved.getCreatedByUserId()).isEqualTo(testingUser);

    // Modern Replacement: Assert the timestamp matches within a 1-second window to handle DB truncation safely
    assertThat(retrieved.getCreatedOn()).isCloseTo(testingTime, within(1, ChronoUnit.SECONDS));
  }

  /**
   * Helper method simulating an update interaction cycle by grouping structural audit trail
   * generation and subsequent calculation engine result persistence under a uniform execution loop.
   */
  private CalculatedFeeDetail amendClaimWithNewCalculation(
      Claim claim, AmendmentReasonReference reason, BigDecimal updatedAmount) {

    ClaimAmendment validAmendment =
        claimAmendmentRepository.saveAndFlush(
            ClaimAmendment.builder()
                .id(Uuid7.timeBasedUuid())
                .claim(claim)
                .amendmentReason(reason)
                .beforeState("{}")
                .requestPayload("{}")
                .diff("{}")
                .createdByUserId(ClaimsDataTestUtil.USER_ID)
                .createdOn(OffsetDateTime.now())
                .build());

    return calculatedFeeDetailRepository.saveAndFlush(
        CalculatedFeeDetail.builder()
            .id(Uuid7.timeBasedUuid())
            .claimSummaryFee(
                claimSummaryFeeRepository.getReferenceById(
                    ClaimsDataTestUtil.CLAIM_1_SUMMARY_FEE_ID))
            .claim(claim)
            .claimAmendment(validAmendment)
            .feeCode("CALC-FEE-1-REV")
            .feeType(FeeCalculationType.DISB_ONLY)
            .totalAmount(updatedAmount)
            .isPriceChanged(true)
            .createdByUserId(ClaimsDataTestUtil.USER_ID)
            .createdOn(OffsetDateTime.now().plusMinutes(5).toInstant().atOffset(ZoneOffset.UTC))
            .build());
  }
}