package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFileReadException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CategoryCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MediationType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlMatterStarts;

/** Deserializer which handles deserialization of bulk submission matter starts from XML files. */
public class XmlMatterStartsDeserializer extends JsonDeserializer<XmlMatterStarts> {

  @Override
  public XmlMatterStarts deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    String scheduleRef = null;
    String procurementArea = null;
    String accessPoint = null;
    CategoryCode categoryCode = null;
    String deliveryLocation = null;
    MediationType mediationType = null;
    Integer numberOfMatterStarts = null;

    XmlMapper mapper = (XmlMapper) p.getCodec();

    JsonNode node = mapper.readTree(p);

    JsonNode matterStartNode = node.get("matterStart");
    if (matterStartNode == null) {
      throw new BulkSubmissionFileReadException("MatterStart node not found in XML.");
    }

    Iterable<JsonNode> matterStarts;
    if (matterStartNode.isArray()) {
      matterStarts = matterStartNode;
    } else {
      matterStarts = List.of(matterStartNode);
    }

    for (JsonNode matterStart : matterStarts) {

      JsonNode codeNode = matterStart.get("code");
      JsonNode valueNode = matterStart.get("");

      if (codeNode == null) {
        throw new BulkSubmissionFileReadException("MatterStart does not have a code.");
      }

      String name = codeNode.asText();
      String value = valueNode == null ? null : valueNode.asText().trim();

      switch (name) {
        case "SCHEDULE_REF" -> scheduleRef = value;
        case "PROCUREMENT_AREA" -> procurementArea = value;
        case "ACCESS_POINT" -> accessPoint = value;
        case "DELIVERY_LOCATION" -> deliveryLocation = value;
        default -> {
          try {
            if (isCategoryCode(name)) {
              categoryCode = CategoryCode.valueOf(name);
              numberOfMatterStarts = Integer.parseInt(value);
            } else {
              mediationType =
                  findMediationType(name)
                      .orElseThrow(
                          () ->
                              new BulkSubmissionFileReadException(
                                  "Unsupported matter start category code/mediation type: '%s'"
                                      .formatted(name)));
              numberOfMatterStarts = Integer.parseInt(value);
            }
          } catch (Exception e) {
            throw new BulkSubmissionFileReadException(
                "Error processing matter start item with code '%s' and value '%s': %s"
                    .formatted(name, value, e.getMessage()),
                e);
          }
        }
      }
    }

    return new XmlMatterStarts(
        scheduleRef,
        procurementArea,
        accessPoint,
        categoryCode,
        deliveryLocation,
        mediationType,
        numberOfMatterStarts);
  }

  private boolean isCategoryCode(String value) {
    try {
      CategoryCode.valueOf(value);
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  private Optional<MediationType> findMediationType(String value) {
    return Arrays.stream(MediationType.values())
        .filter(type -> type.name().startsWith(value + "_"))
        .findFirst();
  }
}
