package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.util.ResourceUtils.getFile;
import static uk.gov.justice.laa.dstew.payments.claimsdata.converter.ConverterTestUtils.getContent;
import static uk.gov.justice.laa.dstew.payments.claimsdata.converter.ConverterTestUtils.getMultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFileReadException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvSubmission;

public class BulkSubmissionCsvConverterTests {

  ObjectMapper objectMapper;

  BulkSubmissionCsvConverter bulkSubmissionCsvConverter;

  private static final String OUTCOMES_INPUT_FILE = "classpath:test_upload_files/csv/outcomes.csv";
  private static final String OUTCOMES_INPUT_FILE_TXT =
      "classpath:test_upload_files/txt/outcomes.txt";
  private static final String OUTCOMES_CONVERTED_FILE =
      "classpath:test_upload_files/csv/outcomes_converted.json";

  private static final String OUTCOMES_WITH_EMPTY_BOTTOM_ROWS_INPUT_FILE =
      "classpath:test_upload_files/csv/outcomes_with_empty_bottom_rows.csv";
  private static final String OUTCOMES_WITH_EMPTY_SPARSE_ROWS_INPUT_FILE =
      "classpath:test_upload_files/csv/outcomes_with_empty_sparse_rows.csv";
  private static final String OUTCOMES_WITH_EMPTY_SPARSE_ROWS_CONVERTED_FILE =
      "classpath:test_upload_files/csv/outcomes_with_empty_sparse_rows_converted.json";

  private static final String MATTERSTARTS_INPUT_FILE =
      "classpath:test_upload_files/csv/matterstarts.csv";
  private static final String MATTERSTARTS_CONVERTED_FILE =
      "classpath:test_upload_files/csv/matterstarts_converted.json";

  private static final String IMMIGRATIONCLR_INPUT_FILE =
      "classpath:test_upload_files/csv/immigrationclr.csv";
  private static final String IMMIGRATIONCLR_CONVERTED_FILE =
      "classpath:test_upload_files/csv/immigrationclr_converted.json";

  private static final String ALL_TYPES_INPUT_FILE =
      "classpath:test_upload_files/csv/outcomes_and_matterstarts.csv";
  private static final String ALL_TYPES_INPUT_FILE_TXT =
      "classpath:test_upload_files/txt/outcomes_and_matterstarts.txt";
  private static final String ALL_TYPES_CONVERTED_FILE =
      "classpath:test_upload_files/csv/outcomes_and_matterstarts_converted.json";

  private static final String NIL_SUBMISSION_INPUT_FILE =
      "classpath:test_upload_files/csv/nil_submission.csv";
  private static final String NIL_SUBMISSION_CONVERTED_FILE =
      "classpath:test_upload_files/csv/nil_submission_converted.json";

  private static final String MISSING_OFFICE_INPUT_FILE =
      "classpath:test_upload_files/csv/missing_office.csv";

  private static final String MISSING_SCHEDULE_INPUT_FILE =
      "classpath:test_upload_files/csv/missing_schedule.csv";

  private static final String DUPLICATE_OFFICE_INPUT_FILE =
      "classpath:test_upload_files/csv/duplicate_office.csv";

  private static final String DUPLICATE_SCHEDULE_INPUT_FILE =
      "classpath:test_upload_files/csv/duplicate_schedule.csv";

  private static final String CORRUPTED_FILE = "classpath:test_upload_files/csv/corrupted_file.csv";

  @BeforeEach
  public void init() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    CsvMapper csvMapper = new CsvMapper();
    bulkSubmissionCsvConverter = new BulkSubmissionCsvConverter(objectMapper, csvMapper);
  }

  @Nested
  @DisplayName("convert")
  class Convert {

    @Test
    @DisplayName("Can convert a bulk submission file with outcomes to csv submission")
    void canConvertOutcomesToCsvSubmission() throws IOException {
      runTest(OUTCOMES_INPUT_FILE, OUTCOMES_CONVERTED_FILE);
    }

    @Test
    @DisplayName("Can convert a bulk submission file with matterstarts to csv submission")
    void canConvertMatterStartsToCsvSubmission() throws IOException {
      runTest(MATTERSTARTS_INPUT_FILE, MATTERSTARTS_CONVERTED_FILE);
    }

    @Test
    @DisplayName("Can convert a bulk submission file with immigration clr rows to csv submission")
    void canConvertImmigrationClrToCsvSubmission() throws IOException {
      runTest(IMMIGRATIONCLR_INPUT_FILE, IMMIGRATIONCLR_CONVERTED_FILE);
    }

    @Test
    @DisplayName(
        "Can convert a bulk submission file with outcomes and matterstarts to csv submission")
    void canConvertAllTypesToCsvSubmission() throws IOException {
      runTest(ALL_TYPES_INPUT_FILE, ALL_TYPES_CONVERTED_FILE);
    }

    @Test
    @DisplayName(
        "Can convert a bulk submission file with nil submission (office and schedule only)")
    void canConvertNilSubmission() throws IOException {
      runTest(NIL_SUBMISSION_INPUT_FILE, NIL_SUBMISSION_CONVERTED_FILE);
    }

    @Test
    @DisplayName("Can convert a bulk submission text file with outcomes to a valid submission")
    void canConvertTxtOutcomesToValidSubmission() throws IOException {
      runTest(OUTCOMES_INPUT_FILE_TXT, OUTCOMES_CONVERTED_FILE);
    }

    @Test
    @DisplayName(
        "Can convert a bulk submission text file with outcomes and matter starts to a valid submission")
    void canConvertTxtFileWithOutcomesAndMatterStartsToValidSubmission() throws IOException {
      runTest(ALL_TYPES_INPUT_FILE_TXT, ALL_TYPES_CONVERTED_FILE);
    }

    @Test
    @DisplayName("Throws exception when office is missing")
    void throwsExceptionWhenOfficeMissing() throws IOException {
      MultipartFile file = getMultipartFile(MISSING_OFFICE_INPUT_FILE);
      assertThrows(
          BulkSubmissionFileReadException.class,
          () -> bulkSubmissionCsvConverter.convert(file),
          "Expected exception to be thrown when office is missing");
    }

    @Test
    @DisplayName("Throws exception when schedule is missing")
    void throwsExceptionWhenScheduleMissing() throws IOException {
      MultipartFile file = getMultipartFile(MISSING_SCHEDULE_INPUT_FILE);
      assertThrows(
          BulkSubmissionFileReadException.class,
          () -> bulkSubmissionCsvConverter.convert(file),
          "Expected exception to be thrown when schedule is missing");
    }

    @Test
    @DisplayName("Throws exception when file does not contain valid entries")
    void throwsExceptionWhenFileIsInvalid() throws IOException {
      MultipartFile file = getMultipartFile(CORRUPTED_FILE);
      assertThrows(
          BulkSubmissionFileReadException.class,
          () -> bulkSubmissionCsvConverter.convert(file),
          "Unable to read file");
    }

    @Test
    @DisplayName("Throws exception when multiple offices found")
    void throwsExceptionForMultipleOffices() throws IOException {
      MultipartFile file = getMultipartFile(DUPLICATE_OFFICE_INPUT_FILE);
      assertThrows(
          BulkSubmissionFileReadException.class,
          () -> bulkSubmissionCsvConverter.convert(file),
          "Expected exception to be thrown when multiple offices found");
    }

    @Test
    @DisplayName("Throws exception when multiple schedules found")
    void throwsExceptionForMultipleSchedules() throws IOException {
      MultipartFile file = getMultipartFile(DUPLICATE_SCHEDULE_INPUT_FILE);
      assertThrows(
          BulkSubmissionFileReadException.class,
          () -> bulkSubmissionCsvConverter.convert(file),
          "Expected exception to be thrown when multiple schedules found");
    }

    @ParameterizedTest
    @CsvSource({
      "classpath:test_upload_files/csv/outcome_missing_matter_type.csv,", // matterType null
      "classpath:test_upload_files/csv/outcome_empty_matter_type.csv,''", // matterType empty
      "classpath:test_upload_files/csv/outcomes_with_headers_only_rows.csv,IALB:IFRA", // matterType
      // present
      "classpath:test_upload_files/csv/outcomes_with_headers_only_sparse_rows.csv," // matterType
      // null
    })
    @DisplayName("Should parse empty or missing matter type code")
    void shouldParseEmptyOrMissingMatterTypeCode(String inputFile, String expectedMatterTypeCode)
        throws IOException {
      MultipartFile file = getMultipartFile(inputFile);
      CsvSubmission bulkSubmission = bulkSubmissionCsvConverter.convert(file);

      assertThat(bulkSubmission.outcomes().getFirst().matterType())
          .isEqualTo(expectedMatterTypeCode);
    }

    @Test
    @DisplayName("Can convert a bulk submission file with empty bottom rows to csv submission")
    void canConvertOutcomesWithEmptyBottomRowsToCsvSubmission() throws IOException {
      runTest(OUTCOMES_WITH_EMPTY_BOTTOM_ROWS_INPUT_FILE, OUTCOMES_CONVERTED_FILE);
    }

    @Test
    @DisplayName("Can convert a bulk submission file with empty sparse rows to csv submission")
    void canConvertOutcomesWithEmptySparseRowsToCsvSubmission() throws IOException {
      runTest(
          OUTCOMES_WITH_EMPTY_SPARSE_ROWS_INPUT_FILE,
          OUTCOMES_WITH_EMPTY_SPARSE_ROWS_CONVERTED_FILE);
    }
  }

  private void runTest(String inputFileName, String outputFileName) throws IOException {
    MultipartFile file = getMultipartFile(inputFileName);
    CsvSubmission bulkSubmission = bulkSubmissionCsvConverter.convert(file);
    String actual = objectMapper.writeValueAsString(bulkSubmission);

    File convertedFile = getFile(outputFileName);
    String expected = getContent(convertedFile);

    JsonNode expectedNode = objectMapper.readTree(expected);
    JsonNode actualNode = objectMapper.readTree(actual);

    assertEquals(expectedNode, actualNode);
  }

  @Nested
  @DisplayName("handles")
  class Handles {

    @Test
    @DisplayName("Returns true for csv extensions")
    void handlesCsv() {
      assertTrue(bulkSubmissionCsvConverter.handles(FileExtension.CSV));
    }

    @Test
    @DisplayName("Returns true for txt extensions")
    void handlesTxt() {
      assertTrue(bulkSubmissionCsvConverter.handles(FileExtension.TXT));
    }

    @Test
    @DisplayName("Returns false for xml extensions")
    void doesNotHandleXml() {
      assertFalse(bulkSubmissionCsvConverter.handles(FileExtension.XML));
    }
  }
}
