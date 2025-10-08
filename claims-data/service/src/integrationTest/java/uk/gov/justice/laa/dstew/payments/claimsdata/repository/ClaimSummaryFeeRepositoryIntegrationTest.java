package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_2_ID;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClaimSummaryFeeRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Test
  void findByClaimId_returnsSummaryFee() {
    var submission = getSubmissionTestData();
    createClaimsTestData(submission);

    var result = claimSummaryFeeRepository.findByClaimId(CLAIM_1_ID);

    assertThat(result).isPresent();
    ClaimSummaryFee summaryFee = result.get();
    assertThat(summaryFee.getClaim().getId()).isEqualTo(CLAIM_1_ID);
    assertThat(summaryFee.getAdviceTime()).isEqualTo(120);
    assertThat(summaryFee.getMeetingsAttendedCode()).isEqualTo("MEET-A");
  }

  @Test
  void findByClaim_returnsSummaryFee() {
    var submission = getSubmissionTestData();
    createClaimsTestData(submission);

    var claim = claimRepository.findById(CLAIM_2_ID).orElseThrow();
    var result = claimSummaryFeeRepository.findByClaim(claim);

    assertThat(result).isPresent();
    assertThat(result.get().getAdviceTypeCode()).isEqualTo("ADV-002");
  }

  @Test
  void findByClaimId_whenUnknown_returnsEmpty() {
    var submission = getSubmissionTestData();
    createClaimsTestData(submission);

    UUID unknownClaimId = Uuid7.timeBasedUuid();
    var result = claimSummaryFeeRepository.findByClaimId(unknownClaimId);

    assertThat(result).isEmpty();
  }
}
