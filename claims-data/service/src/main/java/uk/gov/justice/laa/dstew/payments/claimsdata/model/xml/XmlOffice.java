package uk.gov.justice.laa.dstew.payments.claimsdata.model.xml;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Record holding details of the office submitting a claim.
 *
 * @param account the account number of the office.
 * @param schedule the schedule details for the office.
 */
@JacksonXmlRootElement(localName = "office")
public record XmlOffice(@JacksonXmlProperty(isAttribute = true) String account,
    @JacksonXmlProperty @JsonProperty(required = true) XmlSchedule schedule) {}
