package uk.gov.justice.laa.dstew.payments.claimsdata.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionInvalidFileException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionValidationException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
  @ValueSource(strings = {"test.xml", "test.XML"})
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
  @ValueSource(strings = {"test.csv", "test.CSV"})
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
  @ValueSource(strings = {"test.txt", "test.json", "test.pdf"})
  @DisplayName("Should throw exception for unsupported file extensions")
  void shouldThrowExceptionForUnsupportedFileExtensions(String fileName) {
    // Given
    MockMultipartFile file =
        new MockMultipartFile(
            "file", fileName, "application/json", "content".getBytes(StandardCharsets.UTF_8));

    // When / Then
    assertThatThrownBy(() -> bulkSubmissionFileValidator.validate(file))
        .isInstanceOf(BulkSubmissionInvalidFileException.class)
        .hasMessage("Only .csv and .xml files are allowed");
  }

  @ParameterizedTest
  @ValueSource(strings = {"text/xml", "application/xml", "text/plain"})
  @DisplayName("Should throw exception if MIME type does not match .csv extension")
  void shouldThrowExceptionIfMimeDoesNotMatchCsv(String mimeType) {
    // Given
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "test.csv", mimeType, "col1,col2".getBytes(StandardCharsets.UTF_8));

    // When / Then
    assertThatThrownBy(() -> bulkSubmissionFileValidator.validate(file))
        .isInstanceOf(BulkSubmissionInvalidFileException.class)
        .hasMessage("Mime type does not match the .csv file extension");
  }

  @ParameterizedTest
  @ValueSource(strings = {"text/csv", "text/plain"})
  @DisplayName("Should throw exception if MIME type does not match .xml extension")
  void shouldThrowExceptionIfMimeDoesNotMatchXml(String mimeType) {
    // Given
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "test.xml", mimeType, "<p></p>".getBytes(StandardCharsets.UTF_8));

    // When / Then
    assertThatThrownBy(() -> bulkSubmissionFileValidator.validate(file))
        .isInstanceOf(BulkSubmissionInvalidFileException.class)
        .hasMessage("Mime type does not match the .xml file extension");
  }
}
