package uk.gov.justice.laa.dstew.payments.claimsdata.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimBadRequestException;

class ClaimRetentionLookupValidatorTest {

  private final ClaimRetentionLookupValidator validator = new ClaimRetentionLookupValidator();

  @Test
  void shouldRejectMissingOfficeCode() {
    assertThatThrownBy(() -> validator.validate(null, "010125/001"))
        .isInstanceOfSatisfying(
            ClaimBadRequestException.class,
            exception ->
                assertThat(exception.getMessage())
                    .isEqualTo(ClaimRetentionLookupValidator.OFFICE_CODE_REQUIRED));
  }

  @Test
  void shouldRejectMalformedOfficeCode() {
    assertThatThrownBy(() -> validator.validate("invalid", "010125/001"))
        .isInstanceOfSatisfying(
            ClaimBadRequestException.class,
            exception ->
                assertThat(exception.getMessage())
                    .isEqualTo(ClaimRetentionLookupValidator.OFFICE_CODE_INVALID));
  }

  @Test
  void shouldRejectMissingUfn() {
    assertThatThrownBy(() -> validator.validate("0R695K", null))
        .isInstanceOfSatisfying(
            ClaimBadRequestException.class,
            exception ->
                assertThat(exception.getMessage())
                    .isEqualTo(ClaimRetentionLookupValidator.UFN_REQUIRED));
  }

  @Test
  void shouldRejectMalformedUfn() {
    assertThatThrownBy(() -> validator.validate("0R695K", "23041993/001"))
        .isInstanceOfSatisfying(
            ClaimBadRequestException.class,
            exception ->
                assertThat(exception.getMessage())
                    .isEqualTo(ClaimRetentionLookupValidator.UFN_INVALID));
  }

  @Test
  void shouldAcceptValidIdentifiers() {
    validator.validate("0R695K", "010125/001");
  }
}
