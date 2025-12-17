package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.util.ResourceUtils.getFile;
import static uk.gov.justice.laa.dstew.payments.claimsdata.converter.BulkSubmissionConverter.IMMIGRATION_CLR_MISSING_CODE_ATTRIBUTE_ERROR;
import static uk.gov.justice.laa.dstew.payments.claimsdata.converter.BulkSubmissionConverter.MATTER_START_ERROR_MESSAGE_TEMPLATE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.converter.BulkSubmissionConverter.MATTER_START_MISSING_CODE_ATTRIBUTE_ERROR;
import static uk.gov.justice.laa.dstew.payments.claimsdata.converter.BulkSubmissionConverter.MATTER_START_NODE_MISSING_ERROR;
import static uk.gov.justice.laa.dstew.payments.claimsdata.converter.BulkSubmissionConverter.UNSUPPORTED_CATEGORY_CODE_MEDIATION_TYPE_ERROR;
import static uk.gov.justice.laa.dstew.payments.claimsdata.converter.ConverterTestUtils.getContent;
import static uk.gov.justice.laa.dstew.payments.claimsdata.converter.ConverterTestUtils.getMultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFileReadException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlSubmission;

/**
 * Test class for {@link BulkSubmissionXmlConverter} that verifies XML file conversion functionality
 * and handling of different file extensions. This class tests the conversion of bulk submission XML
 * files to {@link XmlSubmission} objects and validates proper handling of various scenarios
 * including invalid inputs, missing data, and different file types.
 *
 * <p>The test suite is divided into two main sections:
 *
 * <ul>
 *   <li>Convert - Tests for XML file conversion functionality
 *   <li>Handles - Tests for file extension support validation
 * </ul>
 */
public class BulkSubmissionXmlConverterTests {

  ObjectMapper objectMapper;

  BulkSubmissionXmlConverter bulkSubmissionXmlConverter;

  private static final String OUTCOMES_INPUT_FILE =
      "classpath:test_upload_files/xml/outcomes_with_client.xml";
  private static final String OUTCOMES_WITH_UNSUPPORTED_NAME =
      "classpath:test_upload_files/xml/outcomes_with_unsupported_name.xml";
  private static final String OUTCOMES_WITH_MISSING_NAME =
      "classpath:test_upload_files/xml/outcomes_with_missing_name.xml";
  private static final String MATTER_STARTS_MEDIATION_TYPE_INPUT_FILE =
      "classpath:test_upload_files/xml/matter_starts_with_mediation_type.xml";
  private static final String MATTER_STARTS_MEDIATION_TYPE_MISSING_COUNT_INPUT_FILE =
      "classpath:test_upload_files/xml/matter_starts_with_missing_count.xml";
  private static final String MATTER_STARTS_MISSING_CODE_INPUT_FILE =
      "classpath:test_upload_files/xml/matter_starts_with_missing_code.xml";
  private static final String MATTER_STARTS_UNKNOWN_CODE_INPUT_FILE =
      "classpath:test_upload_files/xml/matter_starts_with_unknown_code.xml";
  private static final String MATTER_STARTS_NO_DATA_INPUT_FILE =
      "classpath:test_upload_files/xml/matter_starts_with_no_data.xml";
  private static final String MATTER_STARTS_MALFORMED_XML_FILE =
      "classpath:test_upload_files/xml/matter_starts_with_malformed_xml.xml";
  private static final String MATTER_STARTS_ONLY_MEDIATION_TYPES_INPUT_FILE =
      "classpath:test_upload_files/xml/matter_starts_with_only_mediation_types.xml";
  private static final String MATTER_STARTS_CATEGORY_CODE_INPUT_FILE =
      "classpath:test_upload_files/xml/matter_starts_with_category_code.xml";
  private static final String OUTCOMES_CONVERTED_FILE =
      "classpath:test_upload_files/xml/outcomes_with_client_converted.json";
  private static final String OUTCOME_MISSING_MATTER_TYPE_INPUT_FILE =
      "classpath:test_upload_files/xml/outcome_missing_matter_type.xml";
  private static final String MISSING_OUTCOMES_SINGLE_ELEMENT_INPUT_FILE =
      "classpath:test_upload_files/xml/missing_outcomes_single.xml";
  private static final String MISSING_OUTCOMES_DOUBLE_ELEMENT_INPUT_FILE =
      "classpath:test_upload_files/xml/missing_outcomes_double.xml";
  private static final String MISSING_OUTCOMES_CONVERTED_FILE =
      "classpath:test_upload_files/xml/missing_outcomes_converted.json";
  private static final String MATTER_STARTS_MEDIATION_TYPE_CONVERTED_FILE =
      "classpath:test_upload_files/xml/matter_starts_with_mediation_type_converted.json";
  private static final String MATTER_STARTS_CATEGORY_CODE_CONVERTED_FILE =
      "classpath:test_upload_files/xml/matter_starts_with_category_code_converted.json";
  private static final String MATTER_STARTS_ONLY_MEDIATION_TYPES_CONVERTED_FILE =
      "classpath:test_upload_files/xml/matter_starts_with_only_mediation_types_converted.json";
  private static final String IMMIGRATION_CLR_INPUT_FILE =
      "classpath:test_upload_files/xml/immigration_clr.xml";
  private static final String IMMIGRATION_CLR_MISSING_CODE_ATTRIBUTE_INPUT_FILE =
      "classpath:test_upload_files/xml/immigration_clr_missing_code_attribute.xml";
  private static final String IMMIGRATION_CLR_CONVERTED_FILE =
      "classpath:test_upload_files/xml/immigration_clr_converted.json";

  private static final String MISSING_OFFICE_INPUT_FILE =
      "classpath:test_upload_files/xml/missing_office.xml";

  private static final String MISSING_SCHEDULE_INPUT_FILE =
      "classpath:test_upload_files/xml/missing_schedule.xml";
  private static final String MULTIPLE_OFFICES =
      "classpath:test_upload_files/xml/multiple_offices.xml";
  private static final String MULTIPLE_OFFICES_AND_SCHEDULE =
      "classpath:test_upload_files/xml/multiple_off_and_sch.xml";
  private static final String MALFORMED_SUBMISSION =
      "classpath:test_upload_files/xml/malformed-submission.xml";

  /**
   * Initializes the test environment before each test execution. Sets up the ObjectMapper with
   * proper configuration and creates a new instance of BulkSubmissionXmlConverter.
   */
  @BeforeEach
  public void init() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    XmlMapper xmlMapper = new XmlMapper();
    bulkSubmissionXmlConverter = new BulkSubmissionXmlConverter(xmlMapper);
  }

  @Nested
  @DisplayName("convert")
  class Convert {

    @Test
    @DisplayName("Can convert a bulk submission file with outcomes to xml submission")
    void canConvertOutcomesToXmlSubmission() throws IOException {
      MultipartFile file = getMultipartFile(OUTCOMES_INPUT_FILE);
      XmlSubmission bulkSubmissionSubmission = bulkSubmissionXmlConverter.convert(file);
      String actual = objectMapper.writeValueAsString(bulkSubmissionSubmission);

      File convertedFile = getFile(OUTCOMES_CONVERTED_FILE);
      String expected = getContent(convertedFile);

      assertEquals(expected, actual);
    }

    private record MissingOutcomeTestData(String inputFile, String convertedFile) {}

    private static Stream<MissingOutcomeTestData> missingOutcomeTestData() {
      return Stream.of(
          new MissingOutcomeTestData(
              MISSING_OUTCOMES_SINGLE_ELEMENT_INPUT_FILE, MISSING_OUTCOMES_CONVERTED_FILE),
          new MissingOutcomeTestData(
              MISSING_OUTCOMES_DOUBLE_ELEMENT_INPUT_FILE, MISSING_OUTCOMES_CONVERTED_FILE));
    }

    @ParameterizedTest(name = "Throws exception when empty outcome nodes - {0}")
    @MethodSource("missingOutcomeTestData")
    void throwsExceptionWhenMissingOutcomeData(MissingOutcomeTestData testData) throws IOException {
      MultipartFile file = getMultipartFile(testData.inputFile());
      BulkSubmissionFileReadException exception =
          assertThrows(
              BulkSubmissionFileReadException.class,
              () -> bulkSubmissionXmlConverter.convert(file));

      assertThat(exception.getErrorMessage()).contains("Outcome does not contain any data");
    }

    @Test
    void throwsExceptionWhenMissingMatterStart() throws IOException {
      MultipartFile file = getMultipartFile(OUTCOME_MISSING_MATTER_TYPE_INPUT_FILE);
      BulkSubmissionFileReadException exception =
          assertThrows(
              BulkSubmissionFileReadException.class,
              () -> bulkSubmissionXmlConverter.convert(file));
      assertThat(exception.getErrorMessage()).contains("Matter type missing in outcome data.");
    }

    @Test
    @DisplayName("Can convert a bulk submission file with immigration clr data to xml submission")
    void canConvertImmigrationClrFieldsToXmlSubmission() throws IOException {
      MultipartFile file = getMultipartFile(IMMIGRATION_CLR_INPUT_FILE);
      XmlSubmission bulkSubmissionSubmission = bulkSubmissionXmlConverter.convert(file);
      String actual = objectMapper.writeValueAsString(bulkSubmissionSubmission);

      File convertedFile = getFile(IMMIGRATION_CLR_CONVERTED_FILE);
      String expected = getContent(convertedFile);

      assertEquals(expected, actual);
    }

    private record MatterStartsTestData(
        String inputFile, String convertedFile, String displayName) {}

    private static Stream<MatterStartsTestData> matterStartsTestData() {
      return Stream.of(
          new MatterStartsTestData(
              MATTER_STARTS_CATEGORY_CODE_INPUT_FILE,
              MATTER_STARTS_CATEGORY_CODE_CONVERTED_FILE,
              "category code"),
          new MatterStartsTestData(
              MATTER_STARTS_MEDIATION_TYPE_INPUT_FILE,
              MATTER_STARTS_MEDIATION_TYPE_CONVERTED_FILE,
              "mediation type"),
          new MatterStartsTestData(
              MATTER_STARTS_ONLY_MEDIATION_TYPES_INPUT_FILE,
              MATTER_STARTS_ONLY_MEDIATION_TYPES_CONVERTED_FILE,
              "mediation type"));
    }

    @ParameterizedTest(name = "Can convert a bulk submission file with matter starts - {0}")
    @MethodSource("matterStartsTestData")
    void canConvertMatterStartsToXmlSubmissionWithValidCategoryCodeOrMediationType(
        MatterStartsTestData testData) throws IOException {
      MultipartFile file = getMultipartFile(testData.inputFile());
      XmlSubmission bulkSubmissionSubmission = bulkSubmissionXmlConverter.convert(file);
      String actual = objectMapper.writeValueAsString(bulkSubmissionSubmission);

      File convertedFile = getFile(testData.convertedFile());
      String expected = getContent(convertedFile);

      assertEquals(expected, actual);
    }

    private record ExceptionTestData(
        String inputFile, String expectedErrorMessage, String displayName) {}

    private static Stream<ExceptionTestData> exceptionTestData() {
      return Stream.of(
          new ExceptionTestData(
              MATTER_STARTS_MEDIATION_TYPE_MISSING_COUNT_INPUT_FILE,
              MATTER_START_ERROR_MESSAGE_TEMPLATE.formatted(
                  "MDCS", null, "Cannot parse null string"),
              "missing count value"),
          new ExceptionTestData(
              MATTER_STARTS_MISSING_CODE_INPUT_FILE,
              MATTER_START_MISSING_CODE_ATTRIBUTE_ERROR,
              "missing code in matter start node"),
          new ExceptionTestData(
              MATTER_STARTS_UNKNOWN_CODE_INPUT_FILE,
              UNSUPPORTED_CATEGORY_CODE_MEDIATION_TYPE_ERROR.formatted("BLAH"),
              "unknown code in matter start node"),
          new ExceptionTestData(
              MATTER_STARTS_NO_DATA_INPUT_FILE,
              MATTER_START_NODE_MISSING_ERROR,
              "Missing matter start node"),
          new ExceptionTestData(
              MATTER_STARTS_MALFORMED_XML_FILE,
              "Malformed XML / file is corrupt (not well-formed). Please fix XML structure and re-submit.",
              "Malformed XML"),
          new ExceptionTestData(
              OUTCOMES_WITH_UNSUPPORTED_NAME,
              "Unsupported name for outcome item: RANDOM_NAME",
              "Outcomes with unsupported name"),
          new ExceptionTestData(
              OUTCOMES_WITH_MISSING_NAME,
              "Outcome item under matter type INVC does not have a name.",
              "Outcomes with missing name"),
          new ExceptionTestData(
              IMMIGRATION_CLR_MISSING_CODE_ATTRIBUTE_INPUT_FILE,
              IMMIGRATION_CLR_MISSING_CODE_ATTRIBUTE_ERROR,
              "Missing code attribute in immigration clr node"));
    }

    @ParameterizedTest(name = "Throws exception when {0}")
    @MethodSource("exceptionTestData")
    void throwsExceptionForInvalidInput(ExceptionTestData testData) throws IOException {
      MultipartFile file = getMultipartFile(testData.inputFile());
      BulkSubmissionFileReadException exception =
          assertThrows(
              BulkSubmissionFileReadException.class,
              () -> bulkSubmissionXmlConverter.convert(file),
              "Expected exception to be thrown");
      assertThat(exception.getErrorMessage()).contains(testData.expectedErrorMessage());
    }

    @Test
    @DisplayName("Throws exception when office is missing")
    void throwsExceptionWhenOfficeMissing() throws IOException {
      MultipartFile file = getMultipartFile(MISSING_OFFICE_INPUT_FILE);
      var actualException =
          assertThrows(
              BulkSubmissionFileReadException.class,
              () -> bulkSubmissionXmlConverter.convert(file),
              "Expected exception to be thrown when office is missing");
      assertThat(actualException.getErrorMessage())
          .isEqualTo("office missing from xml bulk submission file.");
    }

    @Test
    @DisplayName("Throws exception when schedule is missing")
    void throwsExceptionWhenScheduleMissing() throws IOException {
      MultipartFile file = getMultipartFile(MISSING_SCHEDULE_INPUT_FILE);
      assertThrows(
          BulkSubmissionFileReadException.class,
          () -> bulkSubmissionXmlConverter.convert(file),
          "Expected exception to be thrown when schedule is missing");
    }

    @DisplayName(
        "Throws exception with provided error message when submission has more than one offices")
    @Test
    void throwExceptionForMultipleOffice() throws IOException {
      var file = getMultipartFile(MULTIPLE_OFFICES);
      var actualException =
          assertThrows(
              BulkSubmissionFileReadException.class,
              () -> bulkSubmissionXmlConverter.convert(file));
      assertThat(actualException.getErrorMessage())
          .isEqualTo(
              "Multiple offices found in bulk submission file. Only one office is supported per submission.");
    }

    @DisplayName("Throws exception when submission has multiple offices and schedules")
    @Test
    void throwsExceptionMultipleOfficesAndSchedules() throws IOException {
      var file = getMultipartFile(MULTIPLE_OFFICES_AND_SCHEDULE);
      var actualException =
          assertThrows(
              BulkSubmissionFileReadException.class,
              () -> bulkSubmissionXmlConverter.convert(file));
      assertThat(actualException.getErrorMessage())
          .isEqualTo(
              "Multiple schedule found in bulk submission file. Only one schedule is supported per submission.,"
                  + " Multiple offices found in bulk submission file. Only one office is supported per submission.");
    }

    @DisplayName("Throws exception when submission xml is malformed")
    @Test
    void throwsExceptionMalformedXml() throws IOException {
      var file = getMultipartFile(MALFORMED_SUBMISSION);
      var actualException =
          assertThrows(
              BulkSubmissionFileReadException.class,
              () -> bulkSubmissionXmlConverter.convert(file));
      assertThat(actualException.getErrorMessage())
          .isEqualTo(
              "Malformed XML / file is corrupt (not well-formed). Please fix XML structure and re-submit.");
    }
  }

  @Nested
  @DisplayName("handles")
  class Handles {

    @Test
    @DisplayName("Returns true for xml extensions")
    void handlesXml() {
      assertTrue(bulkSubmissionXmlConverter.handles(FileExtension.XML));
    }

    @Test
    @DisplayName("Returns false for csv extensions")
    void doesNotHandleCsv() {
      assertFalse(bulkSubmissionXmlConverter.handles(FileExtension.CSV));
    }

    @Test
    @DisplayName("Returns false for txt extensions")
    void doesNotHandleTxt() {
      assertFalse(bulkSubmissionXmlConverter.handles(FileExtension.TXT));
    }
  }
}
