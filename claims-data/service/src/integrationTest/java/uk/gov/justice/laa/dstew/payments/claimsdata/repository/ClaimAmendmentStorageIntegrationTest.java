package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationType;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

class ClaimAmendmentStorageIntegrationTest extends AbstractIntegrationTest {

  // Governed reference codes seeded by Flyway migration V41 (requested_by / amendment reason).
  private static final String REQUESTED_BY_PROVIDER = "PROVIDER";
  private static final String REASON_PROVIDER_ERROR = "PROVIDER_ERROR";
  private static final String REQUESTED_BY_CONTRACT_MANAGEMENT = "CONTRACT_MANAGEMENT";
  private static final String REASON_OTHER = "OTHER";

  @Autowired private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    // 1. Establish the baseline claim data trees (BulkSubmission, Submissions, Claims)
    seedClaimsData();

    // 2. Clear out core transaction data rows from previous test passes if needed
    claimAmendmentRepository.deleteAll();

    // Note: amendment reference codes are validated against the governed reference data seeded
    // by Flyway migration V41 (requested_by_reference / amendment_reason_reference).
  }

  @Test
  @Transactional
  void shouldTrackMultipleCalculationsAndReturnLatestCFD() {
    Claim targetClaim = claimRepository.findById(ClaimsDataTestUtil.CLAIM_1_ID).orElseThrow();

    // 1. Create the older calculation record (Set timestamp to 10 minutes ago)
    CalculatedFeeDetail firstFeeUpdate =
        amendClaimWithNewCalculation(targetClaim, BigDecimal.valueOf(500));
    firstFeeUpdate.setCreatedOn(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10));

    if (targetClaim.getCalculatedFeeDetails() == null) {
      targetClaim.setCalculatedFeeDetails(new ArrayList<>());
    }
    targetClaim.getCalculatedFeeDetails().add(firstFeeUpdate);

    claimRepository.saveAndFlush(targetClaim);

    // 2. Create the latest calculation record (Set timestamp to right now)
    CalculatedFeeDetail secondFeeUpdate =
        amendClaimWithNewCalculation(targetClaim, BigDecimal.valueOf(789));
    secondFeeUpdate.setCreatedOn(OffsetDateTime.now(ZoneOffset.UTC));

    targetClaim.getCalculatedFeeDetails().add(secondFeeUpdate);

    claimRepository.saveAndFlush(targetClaim);

    // 3. Clear L1 cache to force Hibernate to rerun the SQL query with the ORDER BY clause
    entityManager.clear();

    // 4. Reload from scratch
    Claim evaluatedClaim = claimRepository.findById(ClaimsDataTestUtil.CLAIM_1_ID).orElseThrow();

    // Assertions pass reliably because timestamps are explicitly distinct
    assertThat(evaluatedClaim.getCalculatedFeeDetails()).hasSize(3); // one existing + 2
    assertThat(evaluatedClaim.getLatestCalculatedFee()).isNotNull();
    assertThat(evaluatedClaim.getLatestCalculatedFee().getTotalAmount())
        .isEqualByComparingTo(BigDecimal.valueOf(789));
    assertThat(evaluatedClaim.getLatestCalculatedFee()).isNotNull();
    // check claim amendment for latest fee
    assertThat(evaluatedClaim.getLatestCalculatedFee().getClaimAmendment()).isNotNull();
  }

  @Test
  void shouldProtectClaimFromConcurrentModifications() {
    Claim claimThreadA = claimRepository.findById(ClaimsDataTestUtil.CLAIM_1_ID).orElseThrow();
    long initialVersion = claimThreadA.getVersion();

    Claim claimThreadB = claimRepository.findById(ClaimsDataTestUtil.CLAIM_1_ID).orElseThrow();
    assertThat(claimThreadB.getVersion()).isEqualTo(initialVersion);

    claimThreadA.setAmended(true);
    claimRepository.saveAndFlush(claimThreadA);

    claimThreadB.setFeeCode("CONCURRENT_AMENDED_CODE");

    assertThatThrownBy(() -> claimRepository.saveAndFlush(claimThreadB))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  @Test
  @Transactional
  void shouldIncrementVersionAndTrackLatestCalculatedFeeOnSuccessfulAmendment() {
    Claim targetClaim = claimRepository.findById(ClaimsDataTestUtil.CLAIM_1_ID).orElseThrow();
    long initialVersion = targetClaim.getVersion();

    if (targetClaim.getCalculatedFeeDetails() == null) {
      targetClaim.setCalculatedFeeDetails(new ArrayList<>());
    }
    int baselineSize = targetClaim.getCalculatedFeeDetails().size();

    BigDecimal amendedAmount = BigDecimal.valueOf(1250);
    CalculatedFeeDetail dynamicFeeUpdate = amendClaimWithNewCalculation(targetClaim, amendedAmount);

    targetClaim.setAmended(true);
    targetClaim.getCalculatedFeeDetails().add(dynamicFeeUpdate);

    claimRepository.saveAndFlush(targetClaim);

    Claim evaluatedClaim = claimRepository.findById(ClaimsDataTestUtil.CLAIM_1_ID).orElseThrow();

    assertThat(evaluatedClaim.getVersion()).isEqualTo(initialVersion + 2);
    assertThat(evaluatedClaim.getCalculatedFeeDetails()).hasSize(baselineSize + 1);
    assertThat(evaluatedClaim.getLatestCalculatedFee()).isNotNull();
    assertThat(evaluatedClaim.getLatestCalculatedFee().getTotalAmount())
        .isEqualByComparingTo(amendedAmount);
  }

  @Test
  @Transactional
  void shouldHaveNullClaimAmendmentInCalcFeeDetailsWhenThereAreNoAmendments() {
    CalculatedFeeDetail cfd =
        calculatedFeeDetailRepository
            .findFirstByClaimIdOrderByCreatedOnDescIdDesc(CLAIM_1_ID)
            .get();
    assertThat(cfd.getClaimAmendment()).isNull();
  }

  @Test
  @Transactional
  void shouldAccuratelyPersistAndTrackAuditingMetadataContext() {
    Claim targetClaim = claimRepository.findById(ClaimsDataTestUtil.CLAIM_1_ID).orElseThrow();
    String testingUser = "INTEGRATION_TEST_USER_99";
    OffsetDateTime testingTime = OffsetDateTime.now(ZoneOffset.UTC);

    ClaimAmendment auditAmendment =
        claimAmendmentRepository.saveAndFlush(
            ClaimAmendment.builder()
                .id(Uuid7.timeBasedUuid())
                .claim(targetClaim)
                .requestedByCode(REQUESTED_BY_CONTRACT_MANAGEMENT)
                .amendmentReasonCode(REASON_OTHER)
                .beforeState("{}")
                .requestPayload("{}")
                .diff("{}")
                .createdByUserId(testingUser)
                .createdOn(testingTime)
                .build());

    claimAmendmentRepository.flush();
    ClaimAmendment retrieved =
        claimAmendmentRepository.findById(auditAmendment.getId()).orElseThrow();

    assertThat(retrieved.getCreatedByUserId()).isEqualTo(testingUser);
    assertThat(retrieved.getCreatedOn()).isCloseTo(testingTime, within(1, ChronoUnit.SECONDS));
  }

  @Test
  void shouldSuccessfullyPersistAndReadLargeComplexJsonbPayloads() {
    Claim targetClaim = claimRepository.findById(ClaimsDataTestUtil.CLAIM_1_ID).orElseThrow();

    String massiveComplexJson =
        """
        {
          "transactionId": "%s",
          "meta": {
            "sourceSystem": "LAA-SUBMIT-A-BULK-CLAIM",
            "environment": "integration-test",
            "nestedArray": [
              {"lineItem": 1, "feeCode": "REVISED-LEGAL-AID-FEE-CODE-MAX", "amount": 125000.75},
              {"lineItem": 2, "feeCode": "ADDITIONAL-DISBURSEMENT-VAT", "amount": 250.00}
            ]
          },
          "adjustments": {
            "reason": "Comprehensive structural audit remediation path loop execution parameters",
            "approvedBy": "%s"
          }
        }
        """
            .formatted(UUID.randomUUID(), ClaimsDataTestUtil.USER_ID);

    ClaimAmendment heavyAmendment =
        claimAmendmentRepository.saveAndFlush(
            ClaimAmendment.builder()
                .id(Uuid7.timeBasedUuid())
                .claim(targetClaim)
                .requestedByCode(REQUESTED_BY_PROVIDER)
                .amendmentReasonCode(REASON_PROVIDER_ERROR)
                .beforeState(massiveComplexJson)
                .requestPayload(massiveComplexJson)
                .diff(massiveComplexJson)
                .createdByUserId(ClaimsDataTestUtil.USER_ID)
                .createdOn(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

    claimAmendmentRepository.flush();

    // 3. Clear persistence context cache to guarantee a raw database block read
    claimAmendmentRepository.flush();
    ClaimAmendment retrievedAmendment =
        claimAmendmentRepository.findById(heavyAmendment.getId()).orElseThrow();

    // 4. Assert that the unique business values survived the JSONB round-trip intact
    assertThat(retrievedAmendment.getBeforeState())
        .contains("REVISED-LEGAL-AID-FEE-CODE-MAX")
        .contains("125000.75")
        .contains("ADDITIONAL-DISBURSEMENT-VAT")
        .contains("Comprehensive structural audit remediation path loop execution parameters");
  }

  private CalculatedFeeDetail amendClaimWithNewCalculation(Claim claim, BigDecimal updatedAmount) {

    ClaimAmendment validAmendment =
        claimAmendmentRepository.saveAndFlush(
            ClaimAmendment.builder()
                .id(Uuid7.timeBasedUuid())
                .claim(claim)
                .requestedByCode(REQUESTED_BY_PROVIDER)
                .amendmentReasonCode(REASON_PROVIDER_ERROR)
                .beforeState("{}")
                .requestPayload("{}")
                .diff("{}")
                .createdByUserId(ClaimsDataTestUtil.USER_ID)
                .createdOn(OffsetDateTime.now(ZoneOffset.UTC))
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
            .createdOn(
                OffsetDateTime.now(ZoneOffset.UTC)
                    .plusMinutes(5)
                    .toInstant()
                    .atOffset(ZoneOffset.UTC))
            .build());
  }
}
