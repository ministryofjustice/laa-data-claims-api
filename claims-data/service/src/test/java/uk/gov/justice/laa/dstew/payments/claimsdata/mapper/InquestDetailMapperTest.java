package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.InquestDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimBadRequestException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimInquestDetail;

@ExtendWith(MockitoExtension.class)
class InquestDetailMapperTest {

  @InjectMocks private InquestDetailMapperImpl mapper = new InquestDetailMapperImpl();

  @Spy private GlobalStringMapper globalStringMapper = new GlobalStringMapperImpl();

  @Test
  void toInquestDetail_null_returnsNull() {
    assertNull(mapper.toInquestDetail(null));
  }

  @Test
  void toInquestDetail_mapsAllFields() {
    final ClaimInquestDetail post =
        new ClaimInquestDetail()
            .deceasedForename("John")
            .deceasedSurname("Doe")
            .deceasedDateOfDeath("29/09/2026")
            .coronersInquestReference("INQ-123");

    final InquestDetail inquestDetail = mapper.toInquestDetail(post);

    assertNotNull(inquestDetail);
    assertEquals("John", inquestDetail.getDeceasedForename());
    assertEquals("Doe", inquestDetail.getDeceasedSurname());
    assertEquals(LocalDate.of(2026, 9, 29), inquestDetail.getDeceasedDateOfDeath());
    assertEquals("INQ-123", inquestDetail.getCoronersInquestReference());
    assertNull(inquestDetail.getClaim());
  }

  @Test
  void toInquestDetail_absentFields_mapToNull() {
    final InquestDetail inquestDetail = mapper.toInquestDetail(new ClaimInquestDetail());

    assertNotNull(inquestDetail);
    assertNull(inquestDetail.getDeceasedForename());
    assertNull(inquestDetail.getDeceasedSurname());
    assertNull(inquestDetail.getDeceasedDateOfDeath());
    assertNull(inquestDetail.getCoronersInquestReference());
  }

  @Test
  void toInquestDetail_blankStrings_mapToNull() {
    final ClaimInquestDetail post =
        new ClaimInquestDetail()
            .deceasedForename("")
            .deceasedSurname(" ")
            .deceasedDateOfDeath("")
            .coronersInquestReference(" ");

    final InquestDetail inquestDetail = mapper.toInquestDetail(post);

    assertNull(inquestDetail.getDeceasedForename());
    assertNull(inquestDetail.getDeceasedSurname());
    assertNull(inquestDetail.getDeceasedDateOfDeath());
    assertNull(inquestDetail.getCoronersInquestReference());
  }

  @Test
  void toInquestDetail_singleDigitDate_isParsed() {
    final InquestDetail inquestDetail =
        mapper.toInquestDetail(new ClaimInquestDetail().deceasedDateOfDeath("5/3/2026"));

    assertEquals(LocalDate.of(2026, 3, 5), inquestDetail.getDeceasedDateOfDeath());
  }

  @Test
  void toInquestDetail_invalidDate_throwsBadRequest() {
    final ClaimInquestDetail post = new ClaimInquestDetail().deceasedDateOfDeath("31/02/2026");

    assertThrows(ClaimBadRequestException.class, () -> mapper.toInquestDetail(post));
  }

  @Test
  void toClaimInquestDetail_null_returnsNull() {
    assertNull(mapper.toClaimInquestDetail(null));
  }

  @Test
  void toClaimInquestDetail_mapsStoredFieldsAndFormatsDate() {
    final InquestDetail entity =
        InquestDetail.builder()
            .deceasedForename("Jane")
            .deceasedSurname("Doe")
            .deceasedDateOfDeath(LocalDate.of(2026, 3, 5))
            .coronersInquestReference("INQ-123")
            .build();

    final ClaimInquestDetail result = mapper.toClaimInquestDetail(entity);

    assertNotNull(result);
    assertEquals("Jane", result.getDeceasedForename());
    assertEquals("Doe", result.getDeceasedSurname());
    assertEquals("05/03/2026", result.getDeceasedDateOfDeath());
    assertEquals("INQ-123", result.getCoronersInquestReference());
    assertNull(result.getIsClientMeansTested());
    assertNotNull(result.getInterestedDepartments());
    assertEquals(0, result.getInterestedDepartments().size());
  }
}
