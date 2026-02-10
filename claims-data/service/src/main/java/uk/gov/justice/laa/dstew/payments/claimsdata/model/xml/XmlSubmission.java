package uk.gov.justice.laa.dstew.payments.claimsdata.model.xml;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileSubmission;

/**
 * Record holding bulk submission details sourced from an XML file.
 *
 * @param schemaLocation schema location from xml headers
 * @param office the office submitting the claim
 */
@XmlRootElement(name = "submission")
@XmlAccessorType(XmlAccessType.FIELD)
@JacksonXmlRootElement(localName = "submission")
public record XmlSubmission(
    @XmlAttribute @JacksonXmlProperty(isAttribute = true) String schemaLocation,
    @XmlElement(name = "office") @JacksonXmlProperty @JsonProperty(required = true)
        XmlOffice office)
    implements FileSubmission {}
