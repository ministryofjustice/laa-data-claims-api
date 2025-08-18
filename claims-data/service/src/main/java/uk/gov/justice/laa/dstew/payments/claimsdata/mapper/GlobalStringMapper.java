package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.mapstruct.Mapper;


/**
 * Global type conversion mapper for use with MapStruct.
 * This mapper provides safe conversions from {@link String} inputs into commonly used types
 * such as {@link Integer}, {@link Long}, {@link java.math.BigDecimal}, and {@link LocalDate}.
 */
@Mapper(componentModel = "spring")
public interface GlobalStringMapper {

  /**
   * Normalises {@link String} values by converting {@code null} or blank ("") strings to {@code null}.
   *
   * @param value the input string
   * @return the original string if non-blank, otherwise {@code null}
   */
  default String map(String value) {
    return (value == null || value.trim().isEmpty()) ? null : value;
  }

  /**
   * Converts a {@link String} to an {@link Integer}, treating {@code null} or blank input as {@code null}.
   *
   * @param value the string to convert
   * @return an {@link Integer} parsed from the string, or {@code null} if input is blank
   * @throws NumberFormatException if the string is non-blank and not a valid integer
   */
  default Integer mapToInteger(String value) {
    return (value == null || value.trim().isEmpty()) ? null : Integer.parseInt(value);
  }

  /**
   * Converts a {@link String} to a {@link Long}, treating {@code null} or blank input as {@code null}.
   *
   * @param value the string to convert
   * @return a {@link Long} parsed from the string, or {@code null} if input is blank
   * @throws NumberFormatException if the string is non-blank and not a valid long
   */
  default Long mapToLong(String value) {
    return (value == null || value.trim().isEmpty()) ? null : Long.parseLong(value);
  }

  /**
   * Converts a {@link String} to a {@link java.math.BigDecimal}, treating {@code null} or blank input as {@code null}.
   *
   * @param value the string to convert
   * @return a {@link java.math.BigDecimal} parsed from the string, or {@code null} if input is blank
   * @throws NumberFormatException if the string is non-blank and not a valid decimal number
   */
  default java.math.BigDecimal mapToBigDecimal(String value) {
    return (value == null || value.trim().isEmpty()) ? null : new java.math.BigDecimal(value);
  }

  /**
   * Converts a {@link String} to a {@link LocalDate}, treating {@code null} or blank input as {@code null}.
   * The expected date format is {@code d/M/yyyy}, e.g. "5/12/2025".
   *
   * @param value the string to convert
   * @return a {@link LocalDate} parsed from the string, or {@code null} if input is blank
   * @throws java.time.format.DateTimeParseException if the string is non-blank and not in the expected format
   */
  default LocalDate stringToLocalDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d/M/yyyy");
    return LocalDate.parse(value, formatter);
  }
}

