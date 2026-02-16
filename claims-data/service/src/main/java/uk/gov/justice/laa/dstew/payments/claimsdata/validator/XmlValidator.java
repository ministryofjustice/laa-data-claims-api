package uk.gov.justice.laa.dstew.payments.claimsdata.validator;

import java.io.IOException;
import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFileReadException;

/**
 * XML validator that validates uploaded files against the LSCSMS bulk load schema.
 *
 * <p>This validator ensures XML submissions conform to the Legal Services Commission Standard
 * Management System (LSCSMS) bulk load schema. The schema is compiled once during class loading and
 * cached for reuse, making subsequent validations more efficient.
 *
 * <p>Thread-safety is ensured by creating new Validator instances for each validation request,
 * while sharing the immutable compiled Schema across all threads.
 *
 * <p>If the XSD schema file is missing or invalid during application startup, the application will
 * fail fast with an appropriate error message.
 */
public final class XmlValidator {

  private static final String XSD_PATH = "schemas/LSCSMSBulkLoadSchemaV3.xsd";

  /** Compiled once and reused (thread-safe). */
  private static final Schema SCHEMA = loadSchema();

  private XmlValidator() {}

  /**
   * Validates an XML file against the LSCSMS schema.
   *
   * <p>This method creates a new thread-safe Validator instance for each validation request and
   * collects any validation errors that occur during the process.
   *
   * @param xmlFile the MultipartFile containing the XML to validate
   * @throws IOException if there are problems reading the XML file
   * @throws BulkSubmissionFileReadException if the XML fails schema validation
   */
  public static void validateXml(MultipartFile xmlFile) throws IOException {

    // Validator is NOT thread-safe -> create per validation
    Validator validator = SCHEMA.newValidator();

    CollectingErrorHandler handler = new CollectingErrorHandler();
    validator.setErrorHandler(handler);

    try (InputStream xmlStream = xmlFile.getInputStream()) {
      Source xmlSource = new StreamSource(xmlStream);
      validator.validate(xmlSource);
    } catch (SAXException ignored) {
      // We suppress the raw SAX exception and throw a clean one instead
    }

    handler.throwIfErrors();
  }

  /**
   * Loads and compiles the XML schema during class initialization.
   *
   * <p>This method runs once during class loading to compile the XSD schema file into a reusable
   * Schema object. It implements a fail-fast approach by throwing an exception if the schema file
   * cannot be found or contains errors.
   *
   * @return the compiled Schema object
   * @throws IllegalStateException if the schema cannot be loaded or compiled
   */
  private static Schema loadSchema() {
    try {
      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

      try (InputStream xsdStream =
          XmlValidator.class.getClassLoader().getResourceAsStream(XSD_PATH)) {

        if (xsdStream == null) {
          throw new IllegalStateException(
              "Critical configuration error: Required XSD not found on classpath: " + XSD_PATH);
        }

        return factory.newSchema(new StreamSource(xsdStream));
      }

    } catch (SAXException | IOException ex) {
      throw new IllegalStateException(
          "Failed to initialise XML validation schema: " + XSD_PATH, ex);
    }
  }
}
