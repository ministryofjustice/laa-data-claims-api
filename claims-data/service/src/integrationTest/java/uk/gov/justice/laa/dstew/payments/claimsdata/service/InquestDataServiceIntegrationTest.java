package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimBadRequestException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimInquestData;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimInquestDataWrite;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;

@Transactional
class InquestDataServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired private InquestDataService inquestDataService;
  @Autowired private ClaimService claimService;
  @PersistenceContext private EntityManager entityManager;

  @BeforeEach
  void setupClaim() {
    seedClaimsData();
  }

  @Test
  void createsHydratesAndReplacesMultiplesWithoutChangingClaimStatus() {
    var originalStatus = claimRepository.findById(CLAIM_1_ID).orElseThrow().getStatus();
    ClaimInquestData created =
        inquestDataService.create(
            CLAIM_1_ID, request(Set.of("MOJ", "HO"), List.of("NHS Trust", "County Council")));

    assertThat(created.getInterestedDepartmentCodes()).containsExactlyInAnyOrder("MOJ", "HO");
    assertThat(created.getInterestedPublicAuthorities())
        .containsExactly("NHS Trust", "County Council");
    assertThat(created.getIsComplete()).isTrue();
    assertThat(created.getCreatedOn()).isNotNull();
    assertThat(created.getUpdatedOn()).isNull();

    ClaimInquestData replaced =
        inquestDataService.replace(CLAIM_1_ID, request(Set.of("AGO"), List.of("Police authority")));

    assertThat(replaced.getInterestedDepartmentCodes()).containsExactly("AGO");
    assertThat(replaced.getInterestedPublicAuthorities()).containsExactly("Police authority");
    assertThat(claimRepository.findById(CLAIM_1_ID).orElseThrow().getStatus())
        .isEqualTo(originalStatus);
    assertThat(replaced.getCreatedOn()).isEqualTo(created.getCreatedOn());
    assertThat(replaced.getUpdatedOn()).isNotNull();
    assertThat(replaced.getUpdatedOn()).isAfterOrEqualTo(replaced.getCreatedOn());
  }

  @Test
  void rejectsAnUnknownDepartmentCode() {
    assertThatThrownBy(
            () ->
                inquestDataService.create(
                    CLAIM_1_ID, request(Set.of("NOT_GOVERNED"), List.of("Authority"))))
        .isInstanceOf(ClaimBadRequestException.class);
  }

  @Test
  void databaseForeignKeyRejectsAnUnknownDepartmentCode() {
    assertThatThrownBy(
            () -> {
              entityManager
                  .createNativeQuery(
                      """
                      INSERT INTO claim_interested_department
                        (id, claim_id, department_code, created_by_user_id)
                      VALUES (:id, :claimId, 'NOT_GOVERNED', 'integration-test')
                      """)
                  .setParameter("id", java.util.UUID.randomUUID())
                  .setParameter("claimId", CLAIM_1_ID)
                  .executeUpdate();
              entityManager.flush();
            })
        .isInstanceOf(PersistenceException.class);
  }

  @Test
  void claimCreationPersistsCompleteInquestDataAndEvaluatesItAsComplete() {
    ClaimInquestData persisted =
        createClaimWithInquestData(
            request(Set.of("MOJ", "HO"), List.of("NHS Trust", "County Council")));

    assertThat(persisted.getDeceasedForename()).isEqualTo("Alex");
    assertThat(persisted.getInterestedDepartmentCodes()).containsExactlyInAnyOrder("MOJ", "HO");
    assertThat(persisted.getInterestedPublicAuthorities())
        .containsExactly("NHS Trust", "County Council");
    assertThat(persisted.getIsComplete()).isTrue();
    assertThat(persisted.getCreatedOn()).isNotNull();
    assertThat(persisted.getUpdatedOn()).isNull();
  }

  @Test
  void claimCreationPersistsPartialInquestDataAndEvaluatesItAsIncomplete() {
    ClaimInquestDataWrite partial =
        new ClaimInquestDataWrite(Set.of(), List.of("County Council"), "integration-test")
            .deceasedSurname("Jones");

    ClaimInquestData persisted = createClaimWithInquestData(partial);

    assertThat(persisted.getDeceasedSurname()).isEqualTo("Jones");
    assertThat(persisted.getInterestedPublicAuthorities()).containsExactly("County Council");
    assertThat(persisted.getIsComplete()).isFalse();
  }

  private ClaimInquestData createClaimWithInquestData(ClaimInquestDataWrite inquestData) {
    var submission = claimRepository.findById(CLAIM_1_ID).orElseThrow().getSubmission();
    submission.setStatus(SubmissionStatus.READY_FOR_SUBMISSION);
    submissionRepository.save(submission);
    ClaimPost claim =
        new ClaimPost()
            .status(ClaimStatus.READY_TO_PROCESS)
            .lineNumber(99)
            .matterTypeCode("INQUEST")
            .createdByUserId("integration-test")
            .inquestData(inquestData);

    var claimId = claimService.createClaim(submission.getId(), claim);

    return inquestDataService.get(claimId);
  }

  private ClaimInquestDataWrite request(Set<String> departments, List<String> authorities) {
    return new ClaimInquestDataWrite(departments, authorities, "integration-test")
        .deceasedForename("Alex")
        .deceasedSurname("Jones")
        .deceasedDateOfBirth(LocalDate.of(1970, 1, 2))
        .deceasedDateOfDeath(LocalDate.of(2025, 3, 4))
        .coronersInquestReference("COR-123");
  }
}
