package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.OFFICE_ACCOUNT_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CalculatedFeeDetailRepositoryIntegrationTest extends AbstractIntegrationTest {

  @BeforeEach
  void setup() {
    seedClaimsData();
  }

  @Test
  void findByClaimId_returnsCalculatedFeeDetail() {
    var result =
        calculatedFeeDetailRepository.findFirstByClaimIdOrderByCreatedOnDescIdDesc(CLAIM_1_ID);

    assertThat(result).isPresent();
    CalculatedFeeDetail feeDetail = result.get();
    assertThat(feeDetail.getClaim().getId()).isEqualTo(CLAIM_1_ID);
    assertThat(feeDetail.getFeeCode()).isEqualTo("CALC-FEE-1");
    assertThat(feeDetail.getTotalAmount()).isEqualByComparingTo("125");
    assertThat(feeDetail.getBoltOnCmrhTelephoneCount()).isEqualTo(2);
  }

  @Test
  void findByClaimId_whenUnknown_returnsEmpty() {
    UUID unknownClaimId = Uuid7.timeBasedUuid();
    var result =
        calculatedFeeDetailRepository.findFirstByClaimIdOrderByCreatedOnDescIdDesc(unknownClaimId);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("findFirstByClaimId prefers higher id when createdOn are equal")
  void findFirstByClaimIdPrefersHigherIdWhenCreatedOnEqual() {
    // create a fresh claim to avoid interference with seeded data
    Instant now = Instant.now();

    Submission testSubmission =
        submissionRepository.saveAndFlush(
            Submission.builder()
                .id(Uuid7.timeBasedUuid())
                .bulkSubmissionId(bulkSubmission.getId())
                .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
                .submissionPeriod("AUG-2026")
                .areaOfLaw(AreaOfLaw.CRIME_LOWER)
                .status(SubmissionStatus.CREATED)
                .providerUserId(bulkSubmission.getCreatedByUserId())
                .createdByUserId(USER_ID)
                .numberOfClaims(1)
                .createdOn(CREATED_ON)
                .build());

    Claim testClaim =
        claimRepository.saveAndFlush(
            Claim.builder()
                .id(Uuid7.timeBasedUuid())
                .submission(testSubmission)
                .caseReferenceNumber("CRN-TST")
                .uniqueFileNumber("UFN-TST")
                .matterTypeCode("TEST-MTC")
                .lineNumber(99)
                .status(ClaimStatus.READY_TO_PROCESS)
                .createdByUserId(USER_ID)
                .build());

    // create a ClaimSummaryFee required for the CFD
    ClaimSummaryFee summaryFee =
        ClaimSummaryFee.builder()
            .claim(testClaim)
            .id(Uuid7.timeBasedUuid())
            .createdByUserId("IT")
            .build();
    claimSummaryFeeRepository.saveAndFlush(summaryFee);

    // create two CFDs with the same createdOn but different IDs; second ID generated later should
    // be higher
    UUID firstId = Uuid7.timeBasedUuid();
    CalculatedFeeDetail first = new CalculatedFeeDetail();
    first.setId(firstId);
    first.setClaim(testClaim);
    first.setClaimSummaryFee(summaryFee);
    first.setCreatedOn(now);
    first.setFeeCode("FEE-1");
    first.setCreatedByUserId("IT");
    calculatedFeeDetailRepository.saveAndFlush(first);

    UUID secondId = Uuid7.timeBasedUuid();
    CalculatedFeeDetail second = new CalculatedFeeDetail();
    second.setId(secondId);
    second.setClaim(testClaim);
    second.setClaimSummaryFee(summaryFee);
    second.setCreatedOn(now);
    second.setFeeCode("FEE-2");
    second.setCreatedByUserId("IT");
    calculatedFeeDetailRepository.saveAndFlush(second);

    // Ensure persistence
    claimRepository.flush();

    var result =
        calculatedFeeDetailRepository.findFirstByClaimIdOrderByCreatedOnDescIdDesc(
            testClaim.getId());

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(secondId);
  }
}
