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

@Transactional
class InquestDataServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired private InquestDataService inquestDataService;
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

    ClaimInquestData replaced =
        inquestDataService.replace(CLAIM_1_ID, request(Set.of("AGO"), List.of("Police authority")));

    assertThat(replaced.getInterestedDepartmentCodes()).containsExactly("AGO");
    assertThat(replaced.getInterestedPublicAuthorities()).containsExactly("Police authority");
    assertThat(claimRepository.findById(CLAIM_1_ID).orElseThrow().getStatus())
        .isEqualTo(originalStatus);
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

  private ClaimInquestDataWrite request(Set<String> departments, List<String> authorities) {
    return new ClaimInquestDataWrite(departments, authorities, "integration-test")
        .deceasedForename("Alex")
        .deceasedSurname("Jones")
        .deceasedDateOfBirth(LocalDate.of(1970, 1, 2))
        .deceasedDateOfDeath(LocalDate.of(2025, 3, 4))
        .coronersInquestReference("COR-123");
  }
}
