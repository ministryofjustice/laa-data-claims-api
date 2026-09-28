package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import jakarta.persistence.EntityManager;
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
  @Autowired private InquestDetailRepository inquestDetailRepository;
  @Autowired private ClaimInterestedDepartmentRepository claimInterestedDepartmentRepository;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void setup() {
    seedClaimsData();
  }

  @Test
  @Transactional
  @DisplayName(
      "saving a claim with inquest details, departments and reference persists")
  void savingClaimPersistsInquestDetailAndInterestedDepartments() {
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    GovernmentDepartmentRef department1Ref =
        governmentDepartmentRefRepository.saveAndFlush(
            GovernmentDepartmentRef.builder()
                .id(UUID.randomUUID())
                .governmentDepartmentCode("CODE")
                .displayLabel("LABEL")
                .isActive(true)
                .displayOrder(1)
                .createdByUserId("TEST")
                .build());
    GovernmentDepartmentRef department2Ref =
        governmentDepartmentRefRepository.saveAndFlush(
            GovernmentDepartmentRef.builder()
                .id(UUID.randomUUID())
                .governmentDepartmentCode("CODE2")
                .displayLabel("LABEL")
                .isActive(true)
                .displayOrder(2)
                .createdByUserId("TEST")
                .build());

    LocalDate deceasedDateOfDeath = LocalDate.of(2024, 1, 2);
    claim.setInquestDetail(
        InquestDetail.builder()
            .id(UUID.randomUUID())
            .claim(claim)
            .deceasedForename("Jane")
            .deceasedSurname("Doe")
            .deceasedDateOfDeath(deceasedDateOfDeath)
            .coronersInquestReference("INQ-123")
            .createdByUserId("TEST")
            .build());
    claim
        .getInterestedDepartments()
        .addAll(
            List.of(
                ClaimInterestedDepartment.builder()
                    .id(UUID.randomUUID())
                    .claim(claim)
                    .governmentDepartment(department1Ref)
                    .displayOrder(1)
                    .createdByUserId("TEST")
                    .build(),
                ClaimInterestedDepartment.builder()
                    .id(UUID.randomUUID())
                    .claim(claim)
                    .governmentDepartment(department2Ref)
                    .displayOrder(2)
                    .createdByUserId("TEST")
                    .build()));

    claimRepository.saveAndFlush(claim);

    entityManager.clear();

    InquestDetail persistedInquestDetail =
        inquestDetailRepository.findByClaimId(CLAIM_1_ID).orElseThrow();
    assertThat(persistedInquestDetail.getDeceasedForename()).isEqualTo("Jane");
    assertThat(persistedInquestDetail.getDeceasedSurname()).isEqualTo("Doe");
    assertThat(persistedInquestDetail.getDeceasedDateOfDeath()).isEqualTo(deceasedDateOfDeath);
    assertThat(persistedInquestDetail.getCoronersInquestReference()).isEqualTo("INQ-123");

    List<ClaimInterestedDepartment> persistedDepartments =
        claimInterestedDepartmentRepository.findByClaimIdOrderByDisplayOrderAsc(CLAIM_1_ID);
    assertThat(persistedDepartments).hasSize(2);
    assertThat(persistedDepartments)
        .extracting(row -> row.getGovernmentDepartment().getId())
        .containsExactly(department1Ref.getId(), department2Ref.getId());
    assertThat(persistedDepartments)
        .extracting(ClaimInterestedDepartment::getDisplayOrder)
        .containsExactly(1, 2);
  }
}
