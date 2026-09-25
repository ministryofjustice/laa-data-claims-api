package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimInterestedDepartment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.GovernmentDepartmentRef;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClaimInterestedDepartmentRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired private ClaimInterestedDepartmentRepository claimInterestedDepartmentRepository;
  @Autowired private GovernmentDepartmentRefRepository governmentDepartmentRefRepository;

  @BeforeEach
  void setup() {
    seedClaimsData();
  }

  @Test
  @DisplayName("findByClaimId returns interested department rows for the claim")
  void findByClaimIdReturnsInterestedDepartmentRowsForTheClaim() {
    var claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    GovernmentDepartmentRef ref =
        governmentDepartmentRefRepository.saveAndFlush(
            GovernmentDepartmentRef.builder()
                .id(UUID.randomUUID())
                .governmentDepartmentCode("CODE")
                .displayLabel("LABEL")
                .isActive(true)
                .displayOrder(1)
                .createdByUserId("TEST")
                .build());

    ClaimInterestedDepartment saved =
        claimInterestedDepartmentRepository.saveAndFlush(
            ClaimInterestedDepartment.builder()
                .id(UUID.randomUUID())
                .claim(claim)
                .governmentDepartment(ref)
                .displayOrder(1)
                .createdByUserId("TEST")
                .build());

    var result = claimInterestedDepartmentRepository.findByClaimId(CLAIM_1_ID);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getId()).isEqualTo(saved.getId());
    assertThat(result.getFirst().getGovernmentDepartment().getId()).isEqualTo(ref.getId());
  }

  @Test
  @DisplayName("findByClaimId when no rows exist returns empty")
  void findByClaimIdWhenNoRowsExistReturnsEmpty() {
    var result = claimInterestedDepartmentRepository.findByClaimId(CLAIM_1_ID);

    assertThat(result).isEmpty();
  }
}
