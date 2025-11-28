package uk.gov.justice.laa.dstew.payments.claimsdata.model.xml;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import uk.gov.justice.laa.dstew.payments.claimsdata.converter.XmlMatterStartsDeserializer;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CategoryCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MediationType;

/**
 * Record representing matter starts data in XML format. Maps to the "newMatterStarts" XML root
 * element for bulk submission processing.
 */
@JacksonXmlRootElement(localName = "newMatterStarts")
@JsonDeserialize(using = XmlMatterStartsDeserializer.class)
public record XmlMatterStarts(
    String scheduleRef,
    String procurementArea,
    String accessPoint,
    CategoryCode categoryCode,
    String deliveryLocation,
    MediationType mediationType,
    Integer numberOfMatterStarts) {}
