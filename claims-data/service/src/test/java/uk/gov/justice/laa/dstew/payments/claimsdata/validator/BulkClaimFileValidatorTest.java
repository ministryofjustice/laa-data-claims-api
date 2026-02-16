package uk.gov.justice.laa.dstew.payments.claimsdata.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionInvalidFileException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionValidationException;

@DisplayName("BulkSubmissionFileValidator Tests")
class BulkSubmissionFileValidatorTest {

  private BulkSubmissionFileValidator bulkSubmissionFileValidator;

  @BeforeEach
  void setUp() {
    bulkSubmissionFileValidator = new BulkSubmissionFileValidator();
  }

  @Test
  @DisplayName("Should throw exception if file is empty")
  void shouldThrowExceptionIfFileIsEmpty() {
    // Given an empty file
    MockMultipartFile file = new MockMultipartFile("file", "test.xml", "text/xml", new byte[0]);

    // When / Then
    assertThatThrownBy(() -> bulkSubmissionFileValidator.validate(file))
        .isInstanceOf(BulkSubmissionValidationException.class)
        .hasMessage("The uploaded file is empty");
  }

  @ParameterizedTest
  @ValueSource(strings = {"test.xml", "test.XML", "test.XML "})
  @DisplayName("Should pass validation for valid .xml files")
  void shouldPassValidationForValidXmlFile(String fileName) {
    // Given
    MockMultipartFile file =
        new MockMultipartFile(
            "file", fileName, "application/xml", "<p></p>".getBytes(StandardCharsets.UTF_8));

    // When / Then - No exception is thrown
    assertThatCode(() -> bulkSubmissionFileValidator.validate(file)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"test.csv", "test.CSV", "test.CSV "})
  @DisplayName("Should pass validation for valid .csv files")
  void shouldPassValidationForValidCsvFile(String fileName) {
    // Given
    MockMultipartFile file =
        new MockMultipartFile(
            "file", fileName, "text/csv", "col1,col2".getBytes(StandardCharsets.UTF_8));

    // When / Then - No exception is thrown
    assertThatCode(() -> bulkSubmissionFileValidator.validate(file)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"test.txt", "test.TXT", "test.TXT "})
  @DisplayName("Should pass validation for valid .txt files")
  void shouldPassValidationForValidTxtFile(String fileName) {
    // Given
    MockMultipartFile file =
        new MockMultipartFile(
            "file", fileName, "text/plain", "col1,col2".getBytes(StandardCharsets.UTF_8));

    // When / Then - No exception is thrown
    assertThatCode(() -> bulkSubmissionFileValidator.validate(file)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"test.json", "test.pdf"})
  @DisplayName("Should throw exception for unsupported file extensions")
  void shouldThrowExceptionForUnsupportedFileExtensions(String fileName) {
    // Given
    MockMultipartFile file =
        new MockMultipartFile(
            "file", fileName, "application/json", "content".getBytes(StandardCharsets.UTF_8));

    // When / Then
    assertThatThrownBy(() -> bulkSubmissionFileValidator.validate(file))
        .isInstanceOf(BulkSubmissionInvalidFileException.class)
        .hasMessage("Only .csv, .xml and .txt files are allowed");
  }

  @ParameterizedTest
  @ValueSource(strings = {"text/xml", "application/xml"})
  @DisplayName("Should throw exception if Content type does not match .csv extension")
  void shouldThrowExceptionIfMimeDoesNotMatchCsv(String contentType) {
    // Given
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "test.csv", contentType, "col1,col2".getBytes(StandardCharsets.UTF_8));

    // When / Then
    assertThatThrownBy(() -> bulkSubmissionFileValidator.validate(file))
        .isInstanceOf(BulkSubmissionInvalidFileException.class)
        .hasMessage("Content type '" + contentType + "' does not match the .csv file extension.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"text/csv", "text/plain"})
  @DisplayName("Should throw exception if Content type does not match .xml extension")
  void shouldThrowExceptionIfMimeDoesNotMatchXml(String contentType) {
    // Given
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "test.xml", contentType, "<p></p>".getBytes(StandardCharsets.UTF_8));

    // When / Then
    assertThatThrownBy(() -> bulkSubmissionFileValidator.validate(file))
        .isInstanceOf(BulkSubmissionInvalidFileException.class)
        .hasMessage("Content type '" + contentType + "' does not match the .xml file extension.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"text/csv", "text/html"})
  @DisplayName("Should throw exception if Content type does not match .xml extension")
  void shouldThrowExceptionIfMimeDoesNotMatchTxt(String contentType) {
    // Given
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "test.txt", contentType, "<p></p>".getBytes(StandardCharsets.UTF_8));

    // When / Then
    assertThatThrownBy(() -> bulkSubmissionFileValidator.validate(file))
        .isInstanceOf(BulkSubmissionInvalidFileException.class)
        .hasMessage("Content type '" + contentType + "' does not match the .txt file extension.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"text/csv", "application/vnd.ms-excel", "text/plain"})
  @DisplayName("Should pass validation for valid .csv files")
  void shouldPassValidationForValidCsvFileContentType(String contentType) {
    // Given
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "test.csv", contentType, "col1,col2".getBytes(StandardCharsets.UTF_8));

    // When / Then - No exception is thrown
    assertThatCode(() -> bulkSubmissionFileValidator.validate(file)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"text/xml", "application/xml"})
  @DisplayName("Should pass validation for valid .xml files")
  void shouldPassValidationForValidXmlFileContentType(String contentType) {
    // Given
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "test.xml", contentType, "col1,col2".getBytes(StandardCharsets.UTF_8));

    // When / Then - No exception is thrown
    assertThatCode(() -> bulkSubmissionFileValidator.validate(file)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"text/plain"})
  @DisplayName("Should pass validation for valid .txt files")
  void shouldPassValidationForValidTxtFileContentType(String contentType) {
    // Given
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "test.txt", contentType, "col1,col2".getBytes(StandardCharsets.UTF_8));

    // When / Then - No exception is thrown
    assertThatCode(() -> bulkSubmissionFileValidator.validate(file)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Should throw exception if Content type is null")
  void shouldThrowExceptionIfContentTypeIsNull() {
    // Given
    MockMultipartFile file =
        new MockMultipartFile("file", "test.txt", null, "<p></p>".getBytes(StandardCharsets.UTF_8));

    // When / Then
    assertThatThrownBy(() -> bulkSubmissionFileValidator.validate(file))
        .isInstanceOf(BulkSubmissionInvalidFileException.class)
        .hasMessage("Content type '' does not match the .txt file extension.");
  }

  @Test
  @DisplayName("Should throw exception if file name is null")
  void shouldThrowExceptionIfFileNameIsNull() {
    /*
     Note: Due to the behavior of the mocking class, the filename is converted to an empty string.
     As a result, this test does not accurately simulate a true null value being received.
     However, the production code correctly handles this scenario.
    */
    MockMultipartFile file =
        new MockMultipartFile(
            "file", null, "text/plain", "<p></p>".getBytes(StandardCharsets.UTF_8));

    // When / Then
    assertThatThrownBy(() -> bulkSubmissionFileValidator.validate(file))
        .isInstanceOf(BulkSubmissionInvalidFileException.class)
        .hasMessage("Only .csv, .xml and .txt files are allowed");
  }
}
