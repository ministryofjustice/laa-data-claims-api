package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ClaimCaseRepositoryIntegrationTest extends AbstractIntegrationTest {

  @BeforeEach
  void setup() {
    seedClaimsData();
  }

  @Test
  void findByClaimId_returnsClaimCase() {
    var result = claimCaseRepository.findByClaimId(CLAIM_1_ID);

    assertThat(result).isPresent();
    ClaimCase claimCase = result.get();
    assertThat(claimCase.getClaim().getId()).isEqualTo(CLAIM_1_ID);
    assertThat(claimCase.getCaseId()).isEqualTo("CASE_ID_1");
    assertThat(claimCase.getUniqueCaseId()).isEqualTo("UC_ID_1");
    assertThat(claimCase.getTransferDate()).isEqualTo(LocalDate.of(2025, 7, 20));
  }

  @Test
  void findByClaimId_whenUnknown_returnsEmpty() {
    UUID unknownClaimId = Uuid7.timeBasedUuid();
    var result = claimCaseRepository.findByClaimId(unknownClaimId);

    assertThat(result).isEmpty();
  }
}
