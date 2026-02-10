package uk.gov.justice.laa.dstew.payments.claimsdata.model.xml;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import uk.gov.justice.laa.dstew.payments.claimsdata.annotation.XsdDocumentation;
import uk.gov.justice.laa.dstew.payments.claimsdata.converter.XmlMatterStartsDeserializer;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CategoryCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MediationType;

/**
 * Record representing matter starts data in XML format. Maps to the "newMatterStarts" XML root
 * element for bulk submission processing.
 */
@XmlRootElement(name = "newMatterStarts")
@XmlAccessorType(XmlAccessType.FIELD)
@JacksonXmlRootElement(localName = "newMatterStarts")
@JsonDeserialize(using = XmlMatterStartsDeserializer.class)
public record XmlMatterStarts(
    @XsdDocumentation(description = "Schedule reference identifier") String scheduleRef,
    @XsdDocumentation(description = "Procurement area") String procurementArea,
    @XsdDocumentation(description = "Access point for service delivery") String accessPoint,
    @XsdDocumentation(description = "Category code for the matter") CategoryCode categoryCode,
    @XsdDocumentation(description = "Service delivery location") String deliveryLocation,
    @XsdDocumentation(description = "Type of mediation") MediationType mediationType,
    @XsdDocumentation(description = "Number of new matter starts") Integer numberOfMatterStarts) {}
