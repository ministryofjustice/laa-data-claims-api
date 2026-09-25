package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.InquestDetail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InquestDetailRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired private InquestDetailRepository inquestDetailRepository;

  @BeforeEach
  void setup() {
    seedClaimsData();
  }

  @Test
  @DisplayName("findByClaimId returns inquest detail")
  void findByClaimIdReturnsInquestDetail() {
    var result = inquestDetailRepository.findByClaimId(CLAIM_1_ID);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("findByClaimId when unknown returns empty")
  void findByClaimIdWhenUnknownReturnsEmpty() {
    UUID unknownClaimId = UUID.randomUUID();
    var result = inquestDetailRepository.findByClaimId(unknownClaimId);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("save and read inquest detail by claim")
  void saveAndReadInquestDetailByClaim() {
    var claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    InquestDetail saved =
        inquestDetailRepository.saveAndFlush(
            InquestDetail.builder()
                .id(UUID.randomUUID())
                .claim(claim)
                .deceasedForename("Jane")
                .deceasedSurname("Doe")
                .deceasedDateOfDeath(LocalDate.of(2024, 1, 2))
                .coronersInquestReference("INQ-123")
                .createdByUserId("TEST")
                .build());

    var result = inquestDetailRepository.findByClaimId(CLAIM_1_ID);

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(saved.getId());
    assertThat(result.get().getDeceasedForename()).isEqualTo("Jane");
    assertThat(result.get().getDeceasedSurname()).isEqualTo("Doe");
  }

  @Test
  @DisplayName("second inquest detail row for the same claim is rejected")
  void secondInquestDetailRowForTheSameClaimIsRejected() {
    var claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    inquestDetailRepository.saveAndFlush(
        InquestDetail.builder().id(UUID.randomUUID()).claim(claim).createdByUserId("TEST").build());

    InquestDetail duplicate =
        InquestDetail.builder().id(UUID.randomUUID()).claim(claim).createdByUserId("TEST").build();

    assertThatThrownBy(() -> inquestDetailRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
