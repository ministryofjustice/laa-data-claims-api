package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;

/**
 * Simple unit tests to exercise the MapStruct mapper and the default date-mapping helper.
 *
 * <p>This test aims to provide complete coverage for the small {@link ValidationClaimMapper}
 * interface: the null / non-null branches of the default {@code map(LocalDate)} helper and the
 * null-input early-return of {@code toValidationClaim}.
 */
@DisplayName("ValidationClaimMapper tests")
class ValidationClaimMapperTest {

  private final ValidationClaimMapper mapper = Mappers.getMapper(ValidationClaimMapper.class);

  @Test
  @DisplayName("map(null) returns null")
  void map_nullDate_returnsNull() {
    assertNull(mapper.map((LocalDate) null));
  }

  @Test
  @DisplayName("map(non-null date) returns ISO-formatted string")
  void map_nonNullDate_formatsIso() {
    LocalDate date = LocalDate.of(2020, Month.JANUARY, 2);
    assertEquals("2020-01-02", mapper.map(date));
  }

  @Test
  @DisplayName("toValidationClaim(null) returns null")
  void toValidationClaim_nullInput_returnsNull() {
    assertNull(mapper.toValidationClaim(null));
  }

  @Test
  @DisplayName("toValidationClaim with minimal snapshot maps without error")
  void toValidationClaim_minimalSnapshot_mapsWithoutError() {
    ClaimStateSnapshot snapshot =
        ClaimStateSnapshot.builder()
            .claimId(UUID.randomUUID())
            // include at least one date to exercise the non-null date-mapping branch inside the
            // generated implementation
            .caseStartDate(LocalDate.of(2020, Month.JANUARY, 2))
            .version(1L)
            .hasAssessment(false)
            .build();

    // The mapper should return a non-null claim instance for a non-null snapshot. We don't need
    // to assert every mapped field here — the intent is to exercise the generated mapping and
    // the default helper in the interface to achieve full coverage for the mapper source.
    Claim claim = mapper.toValidationClaim(snapshot);
    assertNotNull(claim);
    assertEquals(snapshot.getVersion().longValue(), claim.getVersion().longValue());
  }

  @Test
  @DisplayName("toValidationClaim with null version maps without error")
  void toValidationClaim_minimalSnapshot_withNullVersion_mapsWithoutError() {
    ClaimStateSnapshot snapshot =
        ClaimStateSnapshot.builder()
            .claimId(UUID.randomUUID())
            // include at least one date to exercise the non-null date-mapping branch inside the
            // generated implementation
            .caseStartDate(LocalDate.of(2020, Month.JANUARY, 2))
            .version(null)
            .hasAssessment(false)
            .build();

    // Should not throw — mapping should be null-safe for version
    Claim claim = mapper.toValidationClaim(snapshot);
    assertNotNull(claim);
    assertNull(claim.getVersion());
  }
}
