package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import static uk.gov.justice.laa.dstew.payments.claimsdata.converter.BulkSubmissionConverter.IMMIGRATION_CLR_MISSING_CODE_ATTRIBUTE_ERROR;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFileReadException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlImmigrationClr;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.SqlInjectionDetectionUtil;

/**
 * Custom deserializer for converting XML immigration CLR data into {@link XmlImmigrationClr}
 * objects. Handles both single and multiple immigration CLR data entries from XML format.
 */
public class XmlImmigrationClrDeserializer extends JsonDeserializer<XmlImmigrationClr> {

  /**
   * Deserializes immigration CLR data from XML format into an {@link XmlImmigrationClr} object.
   *
   * @param parser the JsonParser used for reading JSON content
   * @param context context that can be used to access information about this deserialization
   *     activity
   * @return an {@link XmlImmigrationClr} object containing the deserialized immigration CLR data
   * @throws IOException if there is an error reading from the JsonParser
   * @throws BulkSubmissionFileReadException if the required 'code' attribute is missing in the XML
   */
  @Override
  public XmlImmigrationClr deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    Map<String, String> immigrationClrData = new LinkedHashMap<>();
    XmlMapper xmlMapper = (XmlMapper) parser.getCodec();
    JsonNode rootNode = xmlMapper.readTree(parser);

    JsonNode immClrDataNode = rootNode.get("immCLRData");
    if (immClrDataNode == null) {
      return new XmlImmigrationClr(immigrationClrData);
    }

    for (JsonNode immClrData : getImmClrDataNodes(immClrDataNode)) {
      processImmClrData(immClrData, immigrationClrData);
    }

    return new XmlImmigrationClr(immigrationClrData);
  }

  private Iterable<JsonNode> getImmClrDataNodes(JsonNode immClrDataNode) {
    return immClrDataNode.isArray() ? immClrDataNode : List.of(immClrDataNode);
  }

  private void processImmClrData(JsonNode immClrData, Map<String, String> immigrationClrData) {
    JsonNode codeNode = immClrData.get("code");
    if (codeNode == null) {
      throw new BulkSubmissionFileReadException(IMMIGRATION_CLR_MISSING_CODE_ATTRIBUTE_ERROR);
    }

    String name = codeNode.asText();
    String value =
        Optional.ofNullable(immClrData.get(""))
            .map(JsonNode::asText)
            .map(String::trim)
            .orElse(null);
    SqlInjectionDetectionUtil.validateNoSqlInjection(name, value);

    immigrationClrData.put(name, value);
  }
}
