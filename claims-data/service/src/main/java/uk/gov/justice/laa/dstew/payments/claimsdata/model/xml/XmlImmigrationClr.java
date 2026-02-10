package uk.gov.justice.laa.dstew.payments.claimsdata.model.xml;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.Map;
import uk.gov.justice.laa.dstew.payments.claimsdata.annotation.XsdDocumentation;
import uk.gov.justice.laa.dstew.payments.claimsdata.converter.XmlImmigrationClrDeserializer;

/**
 * Record representing an Immigration CLR (Controlled Legal Representation) XML element. Used for
 * deserializing immigration CLR data from XML submissions.
 */
@XmlRootElement(name = "immigrationCLR")
@XmlAccessorType(XmlAccessType.FIELD)
@JacksonXmlRootElement(localName = "immigrationCLR")
@JsonDeserialize(using = XmlImmigrationClrDeserializer.class)
public record XmlImmigrationClr(
    @JacksonXmlProperty(localName = "immCLRData")
        @JsonIgnore
        @XsdDocumentation(description = "Immigration CLR data fields")
        Map<String, String> immClrData) {
  /**
   * Returns the map of immigration CLR data fields. This method is used by Jackson to serialize the
   * map entries as individual fields.
   *
   * @return Map containing immigration CLR field names and their values
   */
  @JsonAnyGetter
  public Map<String, String> fields() {
    return immClrData;
  }
}
