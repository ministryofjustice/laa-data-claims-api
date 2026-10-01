package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_2_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_4_ID;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;

/**
 * Verifies persistence of {@link Client#getIsMeansTested()}, added by Flyway migration {@code V51}
 * as a nullable column so existing rows are unaffected until explicitly populated.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientIsMeansTestedIntegrationTest extends AbstractIntegrationTest {

  @Autowired private InquestDetailRepository inquestDetailRepository;
  @Autowired private ClaimInterestedDepartmentRepository claimInterestedDepartmentRepository;

  @BeforeEach
  void setup() {
    seedClaimsData();
  }

  @Test
  @DisplayName(
      "a means-tested inquest claim with no additional inquest details stores"
          + " is_means_tested = true, and no inquest detail or department rows are created")
  void meansTestedClaimStoresTrueWithNoInquestDetailOrDepartmentRows() {
    var claim = claimRepository.findById(CLAIM_2_ID).orElseThrow();

    clientRepository.saveAndFlush(
        Client.builder()
            .id(UUID.randomUUID())
            .claim(claim)
            .isMeansTested(true)
            .createdByUserId("TEST")
            .build());

    Client persisted = clientRepository.findByClaimId(CLAIM_2_ID).orElseThrow();
    assertThat(persisted.getIsMeansTested()).isTrue();
    assertThat(inquestDetailRepository.findByClaimId(CLAIM_2_ID)).isEmpty();
    assertThat(claimInterestedDepartmentRepository.findByClaimIdOrderByDisplayOrderAsc(CLAIM_2_ID))
        .isEmpty();
  }

  @Test
  @DisplayName("a non-means-tested claim's supplied flag is stored as false")
  void nonMeansTestedClaimStoresFalse() {
    var claim = claimRepository.findById(CLAIM_4_ID).orElseThrow();

    clientRepository.saveAndFlush(
        Client.builder()
            .id(UUID.randomUUID())
            .claim(claim)
            .isMeansTested(false)
            .createdByUserId("TEST")
            .build());

    Client persisted = clientRepository.findByClaimId(CLAIM_4_ID).orElseThrow();
    assertThat(persisted.getIsMeansTested()).isFalse();
  }

  @Test
  @DisplayName(
      "an existing claim without is_means_tested keeps its existing values and flag is null")
  void existingClaimWithNoneOfTheNewDataKeepsExistingValuesAndFlagIsNull() {
    Client existingClient = clientRepository.findByClaimId(CLAIM_1_ID).orElseThrow();

    assertThat(existingClient.getIsMeansTested()).isNull();
    assertThat(existingClient.getClientForename()).isEqualTo(SEEDED_CLIENT_FORENAME);
    assertThat(existingClient.getUniqueClientNumber()).isEqualTo(SEEDED_UNIQUE_CLIENT_NUMBER);
  }
}
