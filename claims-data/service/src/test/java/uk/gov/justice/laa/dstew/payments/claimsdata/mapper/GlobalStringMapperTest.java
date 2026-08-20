package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimBadRequestException;

@DisplayName("GlobalStringMapper unit tests")
class GlobalStringMapperTest {

  private final GlobalStringMapper mapper = new GlobalStringMapper() {};

  @Test
  @DisplayName(
      "map should return null for null blank and whitespace-only strings and return value for non-blank")
  void mapNullBlankAndWhitespaceHandled() {
    assertNull(mapper.map(null), "null input should map to null");
    assertNull(mapper.map(""), "empty string should map to null");
    assertNull(mapper.map("   \t \n "), "whitespace-only string should map to null");
    assertEquals("abc", mapper.map("abc"), "non-blank string should be returned as-is");
  }

  @Test
  @DisplayName(
      "mapToInteger should convert valid ints and throw for invalid or out-of-range values")
  void mapToIntegerValidAndInvalid() {
    assertNull(mapper.mapToInteger(null));
    assertNull(mapper.mapToInteger(""));
    assertEquals(Integer.valueOf(123), mapper.mapToInteger("123"));
    assertEquals(Integer.valueOf(-5), mapper.mapToInteger("-5"));

    // Non-numeric should throw NumberFormatException
    assertThrows(NumberFormatException.class, () -> mapper.mapToInteger("abc"));

    // Surrounding whitespace will cause Integer.valueOf to throw
    assertThrows(NumberFormatException.class, () -> mapper.mapToInteger(" 123 "));

    // Too large for int
    assertThrows(NumberFormatException.class, () -> mapper.mapToInteger("999999999999999999999"));
  }

  @Test
  @DisplayName("mapToLong should convert valid longs and throw for invalid values")
  void mapToLongValidAndInvalid() {
    assertNull(mapper.mapToLong(null));
    assertNull(mapper.mapToLong(""));
    assertEquals(Long.valueOf(123L), mapper.mapToLong("123"));

    assertThrows(NumberFormatException.class, () -> mapper.mapToLong("abc"));
    assertThrows(NumberFormatException.class, () -> mapper.mapToLong(" 123 "));
  }

  @Test
  @DisplayName("mapToBigDecimal should convert valid decimals and throw for invalid formats")
  void mapToBigDecimalValidAndInvalid() {
    assertNull(mapper.mapToBigDecimal(null));
    assertNull(mapper.mapToBigDecimal(""));

    assertEquals(new BigDecimal("12.34"), mapper.mapToBigDecimal("12.34"));
    assertEquals(new BigDecimal("-5"), mapper.mapToBigDecimal("-5"));
    assertEquals(new BigDecimal("1E3"), mapper.mapToBigDecimal("1E3"));

    // Comma as decimal separator is invalid for BigDecimal(String)
    assertThrows(NumberFormatException.class, () -> mapper.mapToBigDecimal("12,34"));
  }

  @Test
  @DisplayName(
      "stringToLocalDate should parse valid dates and throw ClaimBadRequestException for invalid formats or values")
  void stringToLocalDateValidAndInvalid() {
    assertNull(mapper.stringToLocalDate(null));
    assertNull(mapper.stringToLocalDate(""));

    LocalDate expected = LocalDate.of(2025, Month.DECEMBER, 5);
    assertEquals(expected, mapper.stringToLocalDate("5/12/2025"));

    // Leading zeros
    assertEquals(LocalDate.of(2020, Month.JANUARY, 5), mapper.stringToLocalDate("05/01/2020"));

    // Invalid format
    ClaimBadRequestException ex1 =
        assertThrows(ClaimBadRequestException.class, () -> mapper.stringToLocalDate("2020-01-01"));
    assertTrue(
        ex1.getMessage().contains("Invalid date value '2020-01-01'. Expected format: d/M/yyyy"));

    // Invalid date values (non-existent date or non-leap year feb 29)
    assertThrows(ClaimBadRequestException.class, () -> mapper.stringToLocalDate("31/2/2020"));
    assertThrows(ClaimBadRequestException.class, () -> mapper.stringToLocalDate("29/2/2019"));

    // Whitespace around date will be treated as non-blank but fail parse
    assertThrows(ClaimBadRequestException.class, () -> mapper.stringToLocalDate(" 5/12/2025 "));
  }
}
