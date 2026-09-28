package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import jakarta.validation.ConstraintViolationException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
  @DisplayName(
      "findByClaimIdOrderByDisplayOrderAsc returns interested department rows for the claim")
  void findByClaimIdOrderByDisplayOrderAscReturnsInterestedDepartmentRowsForTheClaim() {
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

    var result =
        claimInterestedDepartmentRepository.findByClaimIdOrderByDisplayOrderAsc(CLAIM_1_ID);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getId()).isEqualTo(saved.getId());
    assertThat(result.getFirst().getGovernmentDepartment().getId()).isEqualTo(ref.getId());
  }

  @Test
  @DisplayName("findByClaimIdOrderByDisplayOrderAsc when no rows exist returns empty")
  void findByClaimIdOrderByDisplayOrderAscWhenNoRowsExistReturnsEmpty() {
    var result =
        claimInterestedDepartmentRepository.findByClaimIdOrderByDisplayOrderAsc(CLAIM_1_ID);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName(
      "findByClaimIdOrderByDisplayOrderAsc returns rows ordered by displayOrder regardless of"
          + " insertion order")
  void findByClaimIdOrderByDisplayOrderAscReturnsRowsInDisplayOrderRegardlessOfInsertionOrder() {
    var claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    GovernmentDepartmentRef dept1 =
        governmentDepartmentRefRepository.saveAndFlush(
            GovernmentDepartmentRef.builder()
                .id(UUID.randomUUID())
                .governmentDepartmentCode("CODE1")
                .displayLabel("LABEL1")
                .isActive(true)
                .displayOrder(1)
                .createdByUserId("TEST")
                .build());
    GovernmentDepartmentRef dept2 =
        governmentDepartmentRefRepository.saveAndFlush(
            GovernmentDepartmentRef.builder()
                .id(UUID.randomUUID())
                .governmentDepartmentCode("CODE2")
                .displayLabel("LABEL2")
                .isActive(true)
                .displayOrder(2)
                .createdByUserId("TEST")
                .build());
    GovernmentDepartmentRef dept3 =
        governmentDepartmentRefRepository.saveAndFlush(
            GovernmentDepartmentRef.builder()
                .id(UUID.randomUUID())
                .governmentDepartmentCode("CODE3")
                .displayLabel("LABEL3")
                .isActive(true)
                .displayOrder(3)
                .createdByUserId("TEST")
                .build());

    claimInterestedDepartmentRepository.saveAndFlush(
        ClaimInterestedDepartment.builder()
            .id(UUID.randomUUID())
            .claim(claim)
            .governmentDepartment(dept3)
            .displayOrder(3)
            .createdByUserId("TEST")
            .build());
    claimInterestedDepartmentRepository.saveAndFlush(
        ClaimInterestedDepartment.builder()
            .id(UUID.randomUUID())
            .claim(claim)
            .governmentDepartment(dept1)
            .displayOrder(1)
            .createdByUserId("TEST")
            .build());
    claimInterestedDepartmentRepository.saveAndFlush(
        ClaimInterestedDepartment.builder()
            .id(UUID.randomUUID())
            .claim(claim)
            .governmentDepartment(dept2)
            .displayOrder(2)
            .createdByUserId("TEST")
            .build());

    var result =
        claimInterestedDepartmentRepository.findByClaimIdOrderByDisplayOrderAsc(CLAIM_1_ID);

    assertThat(result)
        .extracting(ClaimInterestedDepartment::getDisplayOrder)
        .containsExactly(1, 2, 3);
  }

  @Test
  @DisplayName(
      "departments are all stored and returned in supplied order")
  void repeatedOccurrencesOfSameDepartmentAreAllStoredAndReturnedInSuppliedOrder() {
    var claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    GovernmentDepartmentRef ministryOfJustice =
        governmentDepartmentRefRepository.saveAndFlush(
            GovernmentDepartmentRef.builder()
                .id(UUID.randomUUID())
                .governmentDepartmentCode("MOJ")
                .displayLabel("Ministry of Justice")
                .isActive(true)
                .displayOrder(1)
                .createdByUserId("TEST")
                .build());
    GovernmentDepartmentRef departmentForHealthAndSocialCare =
        governmentDepartmentRefRepository.saveAndFlush(
            GovernmentDepartmentRef.builder()
                .id(UUID.randomUUID())
                .governmentDepartmentCode("DHSC")
                .displayLabel("Department for Health and Social Care")
                .isActive(true)
                .displayOrder(2)
                .createdByUserId("TEST")
                .build());

    ClaimInterestedDepartment occurrence1 =
        claimInterestedDepartmentRepository.saveAndFlush(
            ClaimInterestedDepartment.builder()
                .id(UUID.randomUUID())
                .claim(claim)
                .governmentDepartment(ministryOfJustice)
                .displayOrder(1)
                .createdByUserId("TEST")
                .build());
    ClaimInterestedDepartment occurrence2 =
        claimInterestedDepartmentRepository.saveAndFlush(
            ClaimInterestedDepartment.builder()
                .id(UUID.randomUUID())
                .claim(claim)
                .governmentDepartment(ministryOfJustice)
                .displayOrder(2)
                .createdByUserId("TEST")
                .build());
    ClaimInterestedDepartment occurrence3 =
        claimInterestedDepartmentRepository.saveAndFlush(
            ClaimInterestedDepartment.builder()
                .id(UUID.randomUUID())
                .claim(claim)
                .governmentDepartment(departmentForHealthAndSocialCare)
                .displayOrder(3)
                .createdByUserId("TEST")
                .build());

    var result =
        claimInterestedDepartmentRepository.findByClaimIdOrderByDisplayOrderAsc(CLAIM_1_ID);

    assertThat(result).hasSize(3);
    assertThat(result)
        .extracting(ClaimInterestedDepartment::getId)
        .containsExactly(occurrence1.getId(), occurrence2.getId(), occurrence3.getId());
    assertThat(result)
        .extracting(row -> row.getGovernmentDepartment().getId())
        .containsExactly(
            ministryOfJustice.getId(),
            ministryOfJustice.getId(),
            departmentForHealthAndSocialCare.getId());
  }

  @Test
  @DisplayName(
      "a blank department occurrence is rejected, and the"
          + " remaining populated slots persist in their relative order with no blank row")
  void blankDepartmentOccurrenceIsRejectedAndOrderIsPreserved() {
    var claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();

    ClaimInterestedDepartment blankOccurrence =
        ClaimInterestedDepartment.builder()
            .id(UUID.randomUUID())
            .claim(claim)
            .governmentDepartment(null)
            .displayOrder(1)
            .createdByUserId("TEST")
            .build();

    GovernmentDepartmentRef slot2Department =
        governmentDepartmentRefRepository.saveAndFlush(
            GovernmentDepartmentRef.builder()
                .id(UUID.randomUUID())
                .governmentDepartmentCode("BLANK-SLOT-CODE2")
                .displayLabel("LABEL2")
                .isActive(true)
                .displayOrder(1)
                .createdByUserId("TEST")
                .build());
    GovernmentDepartmentRef slot3Department =
        governmentDepartmentRefRepository.saveAndFlush(
            GovernmentDepartmentRef.builder()
                .id(UUID.randomUUID())
                .governmentDepartmentCode("BLANK-SLOT-CODE3")
                .displayLabel("LABEL3")
                .isActive(true)
                .displayOrder(2)
                .createdByUserId("TEST")
                .build());

    ClaimInterestedDepartment slot2Row =
        claimInterestedDepartmentRepository.saveAndFlush(
            ClaimInterestedDepartment.builder()
                .id(UUID.randomUUID())
                .claim(claim)
                .governmentDepartment(slot2Department)
                .displayOrder(2)
                .createdByUserId("TEST")
                .build());
    ClaimInterestedDepartment slot3Row =
        claimInterestedDepartmentRepository.saveAndFlush(
            ClaimInterestedDepartment.builder()
                .id(UUID.randomUUID())
                .claim(claim)
                .governmentDepartment(slot3Department)
                .displayOrder(3)
                .createdByUserId("TEST")
                .build());

    assertThatThrownBy(() -> claimInterestedDepartmentRepository.saveAndFlush(blankOccurrence))
        .isInstanceOf(ConstraintViolationException.class);

    var result =
        claimInterestedDepartmentRepository.findByClaimIdOrderByDisplayOrderAsc(CLAIM_1_ID);

    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(ClaimInterestedDepartment::getId)
        .containsExactly(slot2Row.getId(), slot3Row.getId());
    assertThat(result).extracting(ClaimInterestedDepartment::getDisplayOrder).containsExactly(2, 3);
    assertThat(result).noneMatch(row -> row.getGovernmentDepartment() == null);
  }

  @Test
  @DisplayName(
      "a department occurrence referencing a nonexistent claim is rejected by the database")
  void occurrenceReferencingNonexistentClaimIsRejected() {
    UUID nonexistentClaimId = UUID.randomUUID();
    GovernmentDepartmentRef ref =
        governmentDepartmentRefRepository.saveAndFlush(
            GovernmentDepartmentRef.builder()
                .id(UUID.randomUUID())
                .governmentDepartmentCode("NONEXISTENT-CLAIM-CODE")
                .displayLabel("LABEL")
                .isActive(true)
                .displayOrder(1)
                .createdByUserId("TEST")
                .build());

    ClaimInterestedDepartment occurrence =
        ClaimInterestedDepartment.builder()
            .id(UUID.randomUUID())
            .claim(claimRepository.getReferenceById(nonexistentClaimId))
            .governmentDepartment(ref)
            .displayOrder(1)
            .createdByUserId("TEST")
            .build();

    assertThatThrownBy(() -> claimInterestedDepartmentRepository.saveAndFlush(occurrence))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName(
      "a department occurrence referencing a nonexistent government department is rejected by"
          + " the database")
  void occurrenceReferencingNonexistentGovernmentDepartmentIsRejected() {
    var claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    UUID nonexistentDepartmentId = UUID.randomUUID();

    ClaimInterestedDepartment occurrence =
        ClaimInterestedDepartment.builder()
            .id(UUID.randomUUID())
            .claim(claim)
            .governmentDepartment(
                governmentDepartmentRefRepository.getReferenceById(nonexistentDepartmentId))
            .displayOrder(1)
            .createdByUserId("TEST")
            .build();

    assertThatThrownBy(() -> claimInterestedDepartmentRepository.saveAndFlush(occurrence))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
