package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_2_ID;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClaimSummaryFeeRepositoryIntegrationTest extends AbstractIntegrationTest {

  @BeforeEach
  void setup() {
    seedClaimsData();
  }

  @Test
  @DisplayName("findByClaimId returns summary fee")
  void findByClaimIdReturnsSummaryFee() {
    var result = claimSummaryFeeRepository.findByClaimId(CLAIM_1_ID);

    assertThat(result).isPresent();
    ClaimSummaryFee summaryFee = result.get();
    assertThat(summaryFee.getClaim().getId()).isEqualTo(CLAIM_1_ID);
    assertThat(summaryFee.getAdviceTime()).isEqualTo(120);
    assertThat(summaryFee.getMeetingsAttendedCode()).isEqualTo(MEETING_ATTENDED_CODE_1);
  }

  @Test
  @DisplayName("findByClaim returns summary fee")
  void findByClaimReturnsSummaryFee() {
    var claim = claimRepository.findById(CLAIM_2_ID).orElseThrow();
    var result = claimSummaryFeeRepository.findByClaim(claim);

    assertThat(result).isPresent();
    assertThat(result.get().getAdviceTypeCode()).isEqualTo("REM");
  }

  @Test
  @DisplayName("findByClaimId when unknown returns empty")
  void findByClaimIdWhenUnknownReturnsEmpty() {
    UUID unknownClaimId = Uuid7.timeBasedUuid();
    var result = claimSummaryFeeRepository.findByClaimId(unknownClaimId);

    assertThat(result).isEmpty();
  }
}
