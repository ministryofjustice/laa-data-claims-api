package uk.gov.justice.laa.dstew.payments.claimsdata.model.xml;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.List;
import uk.gov.justice.laa.dstew.payments.claimsdata.annotation.XsdDocumentation;

/**
 * Record holding bulk submission schedule details.
 *
 * @param submissionPeriod the submission period
 * @param areaOfLaw the area of law for the submission
 * @param scheduleNum the submission schedule number
 * @param outcomes the submission outcomes
 * @param matterStarts the new matter starts
 * @param immigrationClr the immigration CLR data
 */
@XmlRootElement(name = "schedule")
@XmlAccessorType(XmlAccessType.FIELD)
@JacksonXmlRootElement(localName = "schedule")
public record XmlSchedule(
    @XmlAttribute
        @JacksonXmlProperty(isAttribute = true)
        @XsdDocumentation(description = "Submission period (e.g., DEC-2023)", required = true)
        String submissionPeriod,
    @XmlAttribute
        @JacksonXmlProperty(isAttribute = true)
        @XsdDocumentation(description = "Area of law (e.g., MEDIATION, CRIME)", required = true)
        String areaOfLaw,
    @XmlAttribute
        @JacksonXmlProperty(isAttribute = true)
        @XsdDocumentation(description = "Schedule number identifier", required = true)
        String scheduleNum,
    @XmlElement(name = "outcome")
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "outcome")
        @XsdDocumentation(description = "Collection of outcomes for this schedule")
        List<XmlOutcome> outcomes,
    @XmlElement(name = "newMatterStarts")
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "newMatterStarts")
        @XsdDocumentation(description = "New matter starts information")
        List<XmlMatterStarts> matterStarts,
    @XmlElement(name = "immigrationCLR")
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "immigrationCLR")
        @XsdDocumentation(description = "Immigration Controlled Legal Representation data")
        List<XmlImmigrationClr> immigrationClr) {}
