package uk.gov.justice.laa.dstew.payments.claimsdata.model.xml;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Record holding details of the office submitting a claim.
 *
 * @param account the account number of the office.
 * @param schedule the schedule details for the office.
 */
@XmlRootElement(name = "office")
@XmlAccessorType(XmlAccessType.FIELD)
@JacksonXmlRootElement(localName = "office")
public record XmlOffice(
    @XmlAttribute @JacksonXmlProperty(isAttribute = true) String account,
    @XmlElement(name = "schedule") @JacksonXmlProperty @JsonProperty(required = true)
        XmlSchedule schedule) {}
