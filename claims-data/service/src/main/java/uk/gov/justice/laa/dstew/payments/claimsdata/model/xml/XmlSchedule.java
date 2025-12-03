package uk.gov.justice.laa.dstew.payments.claimsdata.model.xml;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.util.List;

/**
 * Record holding bulk submission schedule details.
 *
 * @param submissionPeriod the submission period
 * @param areaOfLaw the area of law for the submission
 * @param scheduleNum the submission schedule number
 * @param outcomes the submission outcomes
 */
@JacksonXmlRootElement(localName = "schedule")
public record XmlSchedule(
    @JacksonXmlProperty(isAttribute = true) String submissionPeriod,
    @JacksonXmlProperty(isAttribute = true) String areaOfLaw,
    @JacksonXmlProperty(isAttribute = true) String scheduleNum,
    @JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "outcome")
        List<XmlOutcome> outcomes,
    @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "newMatterStarts")
        List<XmlMatterStarts> matterStarts,
    @JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "immigrationCLR")
        List<XmlImmigrationClr> immigrationClr) {}
