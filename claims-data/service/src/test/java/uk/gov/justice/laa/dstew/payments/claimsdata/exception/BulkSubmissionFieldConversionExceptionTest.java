package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class BulkSubmissionFieldConversionExceptionTest {

  @DisplayName("Exception without boolean field should return error field and error message")
  @Test
  void testBulkSubmissionFieldConversionException() {

    BulkSubmissionFieldConversionException exception =
        new BulkSubmissionFieldConversionException(
            "Travel Costs Amount must be a valid monetary value", "travelCosts");
    Assertions.assertEquals("travelCosts", exception.getRejectedValue());
    Assertions.assertEquals(
        "Travel Costs Amount must be a valid monetary value", exception.getMessage());
  }

  @DisplayName("Exception with boolean field should return error field and error message")
  @Test
  void testBulkSubmissionFieldConversionExceptionBoolean() {
    BulkSubmissionFieldConversionException exception =
        new BulkSubmissionFieldConversionException("Youth Court", "truee", true);
    Assertions.assertEquals("truee", exception.getRejectedValue());
    Assertions.assertEquals(
        "Invalid value 'truee' supplied for field 'Youth Court'. Valid values are 'Y' or 'N'",
        exception.getMessage());
  }

  @DisplayName(
      "Exception without boolean field should return error field and error message and exception")
  @Test
  void testBulkSubmissionFieldConversionExceptionFieldName() {
    BulkSubmissionFieldConversionException exception =
        new BulkSubmissionFieldConversionException(
            "Travel Costs Amount must be a valid monetary value",
            "travelCosts",
            new RuntimeException());
    Assertions.assertEquals("travelCosts", exception.getRejectedValue());
    Assertions.assertEquals(
        "Travel Costs Amount must be a valid monetary value", exception.getMessage());
  }
}
