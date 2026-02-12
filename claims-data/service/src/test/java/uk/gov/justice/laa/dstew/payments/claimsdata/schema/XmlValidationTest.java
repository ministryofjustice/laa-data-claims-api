package uk.gov.justice.laa.dstew.payments.claimsdata.schema;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.SAXException;

public class XmlValidationTest {

  // Path to your XSD
  private static final String XSD_PATH = "src/main/resources/schemas/LSCSMSBulkLoadSchemaV3.xsd";
  private static final String XML_BASE_PATH = "src/test/resources/test_upload_files/xml/";

  /** Utility method to validate XML against XSD */
  private void validateXml(String xmlPath) throws SAXException, IOException {
    SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    Schema schema = schemaFactory.newSchema(new File(XSD_PATH));
    Validator validator = schema.newValidator();
    validator.validate(new StreamSource(new File(xmlPath)));
  }

  /** Happy path: valid XML should not throw any exception */
  @ParameterizedTest
  @MethodSource("validXmlPaths")
  public void testValidXml(String xmlFileName) {
    assertDoesNotThrow(
        () -> validateXml(XML_BASE_PATH + xmlFileName), "Valid XML should pass XSD validation");
  }

  private static Stream<String> validXmlPaths() {
    return Stream.of(
        "outcomes.xml",
        "outcomes_with_client.xml",
        "schedule_with_nil_outcomes.xml",
        "matter_starts_with_category_code.xml",
        "immigration_clr.xml",
        "matter_starts_with_mediation_type.xml",
        "matter_starts_with_only_mediation_types.xml",
        "outcomes-with_court_location_hpcds-valid.xml",
        "outcomes_with_empty_client_surname.xml",
        "outcomes_with_all_possible_fields.xml");
  }

  /** Negative path: invalid XML should throw SAXException */
  @ParameterizedTest
  @MethodSource("invalidXmlPaths")
  public void testInvalidXml(String xmlFileName) {
    assertThrows(
        SAXException.class,
        () -> validateXml(XML_BASE_PATH + xmlFileName),
        "Invalid XML should fail XSD validation");
  }

  private static Stream<String> invalidXmlPaths() {
    return Stream.of(
        "malformed-submission.xml",
        "invalid_attribute_submission.xml",
        "missing_element_submission.xml",
        "immigration_clr_missing_code_attribute.xml",
        "matter_starts_with_malformed_xml.xml",
        "matter_starts_with_missing_code.xml",
        "matter_starts_with_missing_count.xml",
        "matter_starts_with_no_data.xml",
        "matter_starts_with_unknown_code.xml",
        "missing_office.xml",
        "missing_outcomes_double.xml",
        "missing_outcomes_single.xml",
        "missing_schedule.xml",
        "multiple_off_and_sch.xml",
        "multiple_offices.xml",
        "outcome_empty_matter_type.xml",
        "outcome_missing_matter_type.xml",
        "outcomes-with_court_location-invalid.xml",
        "outcomes_with_missing_name.xml",
        "outcomes_with_undeclared_entity.xml",
        "outcomes_with_unsupported_name.xml",
        "office_with_empty_account_number.xml",
        "office_with_missing_account_attribute.xml",
        "schedule_with_empty_submission_period.xml",
        "schedule_with_missing_submission_period_attribute.xml",
        "schedule_with_invalid_submission_period.xml",
        "schedule_with_invalid_area_of_law.xml",
        "schedule_with_missing_schedule_num.xml",
        "office_with_multiple_schedules.xml");
  }
}
