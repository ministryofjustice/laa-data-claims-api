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

/**
 * Utility class for validating XML files against XSD schemas. Provides functionality to validate
 * XML files submitted through MultipartFile against schema definitions stored in the application's
 * resources.
 */
public class XmlValidator {

  /**
   * Validates the given XML MultipartFile against an XSD in resources.
   *
   * @param xmlFile The uploaded XML file
   * @param xsdPath The classpath location of the XSD (e.g.,
   *     "schemas/BulkSubmissionSchema_v1.0.xsd")
   * @throws SAXException if the XML is invalid
   * @throws IOException if file reading fails
   */
  public static void validateXml(MultipartFile xmlFile, String xsdPath)
      throws SAXException, IOException {
    // Load the XSD from the classpath
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    try (InputStream xsdStream = XmlValidator.class.getClassLoader().getResourceAsStream(xsdPath)) {
      if (xsdStream == null) {
        throw new IOException("XSD file not found in classpath: " + xsdPath);
      }
      Schema schema = factory.newSchema(new StreamSource(xsdStream));

      // Create Validator
      Validator validator = schema.newValidator();

      // Validate the uploaded XML file
      try (InputStream xmlStream = xmlFile.getInputStream()) {
        Source xmlSource = new StreamSource(xmlStream);
        validator.validate(xmlSource);
      }
    }
  }
}
