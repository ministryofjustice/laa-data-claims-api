package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CalculatedFeeDetailRepositoryIntegrationTest extends AbstractIntegrationTest {

  @BeforeEach
  void setup() {
    seedClaimsData();
  }

  @Test
  void findByClaimId_returnsCalculatedFeeDetail() {
    var result = calculatedFeeDetailRepository.findByClaimId(CLAIM_1_ID);

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
    var result = calculatedFeeDetailRepository.findByClaimId(unknownClaimId);

    assertThat(result).isEmpty();
  }
}
