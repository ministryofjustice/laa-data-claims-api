package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimInterestedDepartment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.GovernmentDepartmentRef;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.InquestDetail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClaimInquestPersistenceIntegrationTest extends AbstractIntegrationTest {

  @Autowired private GovernmentDepartmentRefRepository governmentDepartmentRefRepository;

  @BeforeEach
  void setup() {
    seedClaimsData();
  }

  @Test
  @Transactional
  @DisplayName("saving claim persists inquest detail and interested departments")
  void savingClaimPersistsInquestDetailAndInterestedDepartments() {
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    GovernmentDepartmentRef dept1 =
        governmentDepartmentRefRepository.saveAndFlush(
            GovernmentDepartmentRef.builder()
                .id(UUID.randomUUID())
                .governmentDepartmentCode("CODE")
                .displayLabel("LABEL")
                .isActive(true)
                .displayOrder(1)
                .createdByUserId("TEST")
                .build());
    GovernmentDepartmentRef dept2 =
        governmentDepartmentRefRepository.saveAndFlush(
            GovernmentDepartmentRef.builder()
                .id(UUID.randomUUID())
                .governmentDepartmentCode("CODE2")
                .displayLabel("LABEL")
                .isActive(true)
                .displayOrder(2)
                .createdByUserId("TEST")
                .build());

    claim.setInquestDetail(
        InquestDetail.builder()
            .id(UUID.randomUUID())
            .claim(claim)
            .deceasedForename("Jane")
            .deceasedSurname("Doe")
            .deceasedDateOfDeath(LocalDate.of(2024, 1, 2))
            .coronersInquestReference("INQ-123")
            .createdByUserId("TEST")
            .build());

    claim.getInterestedDepartments().addAll(
        List.of(
            ClaimInterestedDepartment.builder()
            .id(UUID.randomUUID())
            .claim(claim)
            .governmentDepartment(dept1)
            .displayOrder(1)
            .createdByUserId("TEST")
            .build(),
            ClaimInterestedDepartment.builder()
            .id(UUID.randomUUID())
            .claim(claim)
            .governmentDepartment(dept2)
            .displayOrder(2)
            .createdByUserId("TEST")
            .build()));

    claimRepository.saveAndFlush(claim);

    Claim reloaded = claimRepository.findById(CLAIM_1_ID).orElseThrow();

    assertThat(reloaded.getInquestDetail()).isNotNull();
    assertThat(reloaded.getInquestDetail().getDeceasedForename()).isEqualTo("Jane");
    assertThat(reloaded.getInterestedDepartments()).hasSize(2);
    assertThat(reloaded.getInterestedDepartments())
        .extracting(ClaimInterestedDepartment::getDisplayOrder)
        .containsExactly(1, 2);
  }
}
