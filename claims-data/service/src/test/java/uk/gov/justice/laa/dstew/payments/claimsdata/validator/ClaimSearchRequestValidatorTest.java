package uk.gov.justice.laa.dstew.payments.claimsdata.validator;

import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.ClaimSearchRequest;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimBadRequestException;

/**
 * Unit tests for {@link ClaimSearchRequestValidator}.
 *
 * <p>These tests exercise all validation branches: null request, missing office code, whitespace
 * handling and case reference number trimming/minimum-length enforcement.
 */
class ClaimSearchRequestValidatorTest {

  private final ClaimSearchRequestValidator validator = new ClaimSearchRequestValidator();

  @Test
  @DisplayName("validate(null) should throw ClaimBadRequestException with missing request message")
  void validate_nullRequest_throws() {
    ClaimBadRequestException ex =
        Assertions.assertThrows(ClaimBadRequestException.class, () -> validator.validate(null));
    Assertions.assertEquals(ClaimSearchRequestValidator.MISSING_SEARCH_REQUEST, ex.getMessage());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "", "\t"})
  @DisplayName("validateOfficeCode should throw when office code is empty or whitespace")
  void validateOfficeCode_missingOrBlank_throws(String officeCode) {
    ClaimBadRequestException ex =
        Assertions.assertThrows(
            ClaimBadRequestException.class, () -> validator.validateOfficeCode(officeCode));
    Assertions.assertEquals(ClaimSearchRequestValidator.MISSING_OFFICE_CODE, ex.getMessage());
  }

  @Test
  @DisplayName(
      "validateOfficeCode(null) should throw ClaimBadRequestException with missing request message")
  void validateOfficeCode_nullRequest_throws() {
    ClaimBadRequestException ex =
        Assertions.assertThrows(
            ClaimBadRequestException.class, () -> validator.validateOfficeCode(null));
    Assertions.assertEquals(ClaimSearchRequestValidator.MISSING_OFFICE_CODE, ex.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"OFF123", "A1", "1"})
  @DisplayName("validateOfficeCode should not throw for valid office codes")
  void validateOfficeCode_valid_noThrow(String officeCode) {
    Assertions.assertDoesNotThrow(() -> validator.validateOfficeCode(officeCode));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   \t  "})
  @DisplayName(
      "validateCaseReferenceNumber should treat null and blank values as absent (no error)")
  void validateCaseReferenceNumber_nullOrBlank_noThrow(String value) {
    Assertions.assertDoesNotThrow(() -> validator.validateCaseReferenceNumber(value));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ab", "  ab  "})
  @DisplayName(
      "validateCaseReferenceNumber should throw when trimmed value is shorter than minimum")
  void validateCaseReferenceNumber_tooShort_throws(String tooShort) {
    ClaimBadRequestException ex =
        Assertions.assertThrows(
            ClaimBadRequestException.class, () -> validator.validateCaseReferenceNumber(tooShort));

    String expected =
        String.format(
            ClaimSearchRequestValidator.CASE_REFERENCE_TOO_SHORT,
            ClaimSearchRequestValidator.MIN_CASE_REFERENCE_LENGTH);
    Assertions.assertEquals(expected, ex.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"  abc  ", "xyz", "  123  "})
  @DisplayName(
      "validateCaseReferenceNumber should trim input and accept values with trimmed length >= min")
  void validateCaseReferenceNumber_trimmedLength_ok(String value) {
    Assertions.assertDoesNotThrow(() -> validator.validateCaseReferenceNumber(value));
  }

  @ParameterizedTest
  @MethodSource("provideRequestsForValidation")
  @DisplayName("validate(request) should behave correctly for various request combinations")
  void validate_request_various(String officeCode, String caseRef, boolean shouldThrow) {
    ClaimSearchRequest request =
        ClaimSearchRequest.builder().officeCode(officeCode).caseReferenceNumber(caseRef).build();

    if (shouldThrow) {
      Assertions.assertThrows(ClaimBadRequestException.class, () -> validator.validate(request));
    } else {
      Assertions.assertDoesNotThrow(() -> validator.validate(request));
    }
  }

  private static Stream<Arguments> provideRequestsForValidation() {
    return Stream.of(
        // missing office -> should throw
        Arguments.of(null, null, true),
        // valid office but case too short -> should throw
        Arguments.of("O1", "ab", true),
        // valid office and valid case (trimmed) -> should not throw
        Arguments.of("O1", " abc ", false),
        // Partial match (user supplies starting substring) - minimum length satisfied -> should not
        // throw
        Arguments.of("OFF1", "ABC", false),
        // Contains match with special characters -> should not throw
        Arguments.of("OFF1", "ATE2/1", false),
        // Case-insensitive match -> should not throw
        Arguments.of("OFF1", "ate2/1", false),
        // Exact match including spaces -> should not throw
        Arguments.of("OFF1", "RAC ATE2/1", false),
        // valid office, no case -> should not throw
        Arguments.of("OFF123", null, false),
        // office whitespace only -> should throw even if case is valid
        Arguments.of("   ", "abcdef", true));
  }
}
