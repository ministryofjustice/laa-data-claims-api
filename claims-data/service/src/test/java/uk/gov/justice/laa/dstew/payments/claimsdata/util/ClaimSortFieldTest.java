package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("ClaimSortField")
class ClaimSortFieldTest {

  @ParameterizedTest
  @EnumSource(ClaimSortField.class)
  @DisplayName("Every field round-trips from its API name and exposes a non-blank entity path")
  void everyFieldRoundTripsFromApiName(ClaimSortField field) {
    assertThat(ClaimSortField.fromApiName(field.getApiName()))
        .as("round-trip for %s", field)
        .contains(field);
    assertThat(field.getEntityPath()).as("entity path for %s", field).isNotBlank();
  }

  @Test
  @DisplayName("effective_total_value maps to the effectiveTotalValue entity property")
  void effectiveTotalValueMapsToClaimColumn() {
    assertThat(ClaimSortField.fromApiName("effective_total_value"))
        .map(ClaimSortField::getEntityPath)
        .contains("effectiveTotalValue");
  }

  @Test
  @DisplayName("An unknown API name resolves to empty")
  void unknownApiNameResolvesToEmpty() {
    assertThat(ClaimSortField.fromApiName("not_a_real_field")).isEmpty();
  }

  @Test
  @DisplayName("A null API name resolves to empty rather than throwing")
  void nullApiNameResolvesToEmpty() {
    assertThat(ClaimSortField.fromApiName(null)).isEqualTo(Optional.empty());
  }
}
