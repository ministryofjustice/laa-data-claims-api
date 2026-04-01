package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ClaimSortField tests")
class ClaimSortFieldTest {

  @Test
  @DisplayName("fromApiName returns correct enum for known api name")
  void fromApiName_known() {
    assertThat(ClaimSortField.fromApiName("office_code")).isPresent();
    assertThat(ClaimSortField.fromApiName("office_code").get().getEntityPath())
        .isEqualTo("submission.officeAccountNumber");
  }

  @Test
  @DisplayName("fromApiName returns empty for unknown api name")
  void fromApiName_unknown() {
    assertThat(ClaimSortField.fromApiName("no_such_field")).isEmpty();
  }
}
