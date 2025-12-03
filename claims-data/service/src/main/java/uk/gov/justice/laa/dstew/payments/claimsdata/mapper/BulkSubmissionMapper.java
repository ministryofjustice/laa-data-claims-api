package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.util.StringUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFieldConversionException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionMatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetailsOffice;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetailsSchedule;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvMatterStarts;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvOffice;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvSchedule;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlImmigrationClr;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlMatterStarts;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlOffice;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlSchedule;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlSubmission;

/** Mapping interface for the mapping of bulk submission objects. */
@Mapper(componentModel = "spring")
public interface BulkSubmissionMapper {
  /**
   * Maps the given {@code FileSubmission} to a {@code GetBulkSubmission200ResponseDetails}.
   *
   * @param submission the java representation of the bulk submission file
   * @return the API model for the bulk submission.
   */
  default GetBulkSubmission200ResponseDetails toBulkSubmissionDetails(FileSubmission submission) {
    if (submission instanceof CsvSubmission csvSubmission) {
      return toBulkSubmissionDetails(csvSubmission);
    } else if (submission instanceof XmlSubmission xmlSubmission) {
      return toBulkSubmissionDetails(xmlSubmission);
    } else {
      throw new IllegalArgumentException("Unsupported submission type: " + submission.getClass());
    }
  }

  /**
   * Maps the given {@code XmlSubmission} to a {@code GetBulkSubmission200ResponseDetails}.
   *
   * @param submission the java representation of the xml bulk submission file
   * @return the API model for the bulk submission.
   */
  @Mapping(target = "schedule", source = "office.schedule")
  @Mapping(target = "outcomes", source = "office.schedule.outcomes")
  @Mapping(target = "matterStarts", source = "office.schedule.matterStarts")
  @Mapping(
      target = "immigrationClr",
      expression = "java(mapImmigrationClrData(submission.office().schedule().immigrationClr()))")
  GetBulkSubmission200ResponseDetails toBulkSubmissionDetails(XmlSubmission submission);

  /**
   * Maps the given {@code CsvSubmission} to a {@code GetBulkSubmission200ResponseDetails}.
   *
   * @param submission the java representation of the csv bulk submission file
   * @return the API model for the bulk submission.
   */
  @Mapping(
      target = "matterStarts",
      source = "matterStarts",
      defaultExpression = "java(new ArrayList<>())")
  @Mapping(
      target = "immigrationClr",
      source = "immigrationClr",
      defaultExpression = "java(new ArrayList<>())")
  GetBulkSubmission200ResponseDetails toBulkSubmissionDetails(CsvSubmission submission);

  /**
   * Map to a {@link GetBulkSubmission200ResponseDetailsOffice}.
   *
   * @param office the {@link XmlOffice} to map.
   * @return a mapped {@link GetBulkSubmission200ResponseDetailsOffice} object.
   */
  GetBulkSubmission200ResponseDetailsOffice toBulkSubmissionOffice(XmlOffice office);

  /**
   * Map to a {@link GetBulkSubmission200ResponseDetailsOffice}.
   *
   * @param office the {@link CsvOffice} to map.
   * @return a mapped {@link GetBulkSubmission200ResponseDetailsOffice} object.
   */
  GetBulkSubmission200ResponseDetailsOffice toBulkSubmissionOffice(CsvOffice office);

  /**
   * Map to a {@link GetBulkSubmission200ResponseDetailsSchedule}.
   *
   * @param schedule the {@link XmlSchedule} to map.
   * @return a mapped {@link GetBulkSubmission200ResponseDetailsSchedule} object.
   */
  GetBulkSubmission200ResponseDetailsSchedule toBulkSubmissionSchedule(XmlSchedule schedule);

  /**
   * Map to a {@link GetBulkSubmission200ResponseDetailsSchedule}.
   *
   * @param schedule the {@link CsvSchedule} to map.
   * @return a mapped {@link GetBulkSubmission200ResponseDetailsSchedule} object.
   */
  GetBulkSubmission200ResponseDetailsSchedule toBulkSubmissionSchedule(CsvSchedule schedule);

  /**
   * Map to a {@link BulkSubmissionOutcome}.
   *
   * @param outcome the {@link XmlOutcome} to map.
   * @return a mapped {@link BulkSubmissionOutcome} object.
   */
  @Mapping(target = "caseStartDate", source = "caseStartDate")
  @Mapping(target = "clientDateOfBirth", source = "clientDateOfBirth")
  @Mapping(target = "workConcludedDate", source = "workConcludedDate")
  @Mapping(target = "transferDate", source = "transferDate")
  @Mapping(target = "surgeryDate", source = "surgeryDate")
  @Mapping(target = "repOrderDate", source = "repOrderDate")
  @Mapping(target = "client2DateOfBirth", source = "client2DateOfBirth")
  @Mapping(target = "medConcludedDate", source = "medConcludedDate")
  @Mapping(
      target = "vatIndicator",
      expression = "java(parseBooleanField(outcome.vatIndicator(), \"VAT Applicable\"))")
  @Mapping(
      target = "londonNonlondonRate",
      expression = "java(parseBooleanField(outcome.londonNonlondonRate(), \"London Rate\"))")
  @Mapping(
      target = "toleranceIndicator",
      expression =
          "java(parseBooleanField(outcome.toleranceIndicator(), \"Tolerance Applicable\"))")
  @Mapping(
      target = "legacyCase",
      expression = "java(parseBooleanField(outcome.legacyCase(), \"Legacy Case\"))")
  @Mapping(
      target = "postalApplAccp",
      expression =
          "java(parseBooleanField(outcome.postalApplAccp(), \"Postal Application Accepted\"))")
  @Mapping(
      target = "substantiveHearing",
      expression = "java(parseBooleanField(outcome.substantiveHearing(), \"Substantive Hearing\"))")
  @Mapping(
      target = "additionalTravelPayment",
      expression =
          "java(parseBooleanField(outcome.additionalTravelPayment(), \"Additional Travel Payment\"))")
  @Mapping(
      target = "clientLegallyAided",
      expression = "java(parseBooleanField(outcome.clientLegallyAided(), \"Is Legally Aided\"))")
  @Mapping(
      target = "client2PostalApplAccp",
      expression =
          "java(parseBooleanField(outcome.client2PostalApplAccp(), \"Client 2 Postal Application Accepted\"))")
  @Mapping(
      target = "dutySolicitor",
      expression = "java(parseBooleanField(outcome.dutySolicitor(), \"Duty Solicitor\"))")
  @Mapping(
      target = "nationalRefMechanismAdvice",
      expression = "java(parseBooleanField(outcome.nationalRefMechanismAdvice(), \"NRM Advice\"))")
  @Mapping(
      target = "ircSurgery",
      expression = "java(parseBooleanField(outcome.ircSurgery(), \"IRC Surgery\"))")
  @Mapping(
      target = "client2LegallyAided",
      expression =
          "java(parseBooleanField(outcome.client2LegallyAided(), \"Client 2 Legally Aided\"))")
  @Mapping(
      target = "eligibleClient",
      expression =
          "java(parseBooleanField(outcome.eligibleClientIndicator(), \"Eligible Client\"))")
  @Mapping(
      target = "youthCourt",
      expression = "java(parseBooleanField(outcome.youthCourt(), \"Youth Court\"))")
  @Mapping(
      target = "adviceTime",
      expression = "java(parseIntegerField(outcome.adviceTime(), \"adviceTime\"))")
  @Mapping(
      target = "travelTime",
      expression = "java(parseIntegerField(outcome.travelTime(), \"travelTime\"))")
  @Mapping(
      target = "waitingTime",
      expression = "java(parseIntegerField(outcome.waitingTime(), \"waitingTime\"))")
  @Mapping(
      target = "profitCost",
      expression = "java(parseBigDecimalField(outcome.profitCost(), \"profitCost\"))")
  @Mapping(
      target = "valueOfCosts",
      expression = "java(parseBigDecimalField(outcome.valueOfCosts(), \"valueOfCosts\"))")
  @Mapping(
      target = "disbursementsAmount",
      expression =
          "java(parseBigDecimalField(outcome.disbursementsAmount(), \"disbursementsAmount\"))")
  @Mapping(
      target = "counselCost",
      expression = "java(parseBigDecimalField(outcome.counselCost(), \"counselCost\"))")
  @Mapping(
      target = "disbursementsVat",
      expression = "java(parseBigDecimalField(outcome.disbursementsVat(), \"disbursementsVat\"))")
  @Mapping(
      target = "travelWaitingCosts",
      expression =
          "java(parseBigDecimalField(outcome.travelWaitingCosts(), \"travelWaitingCosts\"))")
  @Mapping(
      target = "travelCosts",
      expression = "java(parseBigDecimalField(outcome.travelCosts(), \"travelCosts\"))")
  @Mapping(
      target = "adjournedHearingFee",
      expression =
          "java(parseIntegerField(outcome.adjournedHearingFee(), \"adjournedHearingFee\"))")
  @Mapping(
      target = "hoInterview",
      expression = "java(parseIntegerField(outcome.hoInterview(), \"hoInterview\"))")
  @Mapping(
      target = "detentionTravelWaitingCosts",
      expression =
          "java(parseBigDecimalField(outcome.detentionTravelWaitingCosts(), \"detentionTravelWaitingCosts\"))")
  @Mapping(
      target = "medicalReportsClaimed",
      expression =
          "java(parseIntegerField(outcome.medicalReportsClaimed(), \"medicalReportsClaimed\"))")
  @Mapping(
      target = "desiAccRep",
      expression = "java(parseIntegerField(outcome.desiAccRep(), \"desiAccRep\"))")
  @Mapping(
      target = "noOfClients",
      expression = "java(parseIntegerField(outcome.noOfClients(), \"noOfClients\"))")
  @Mapping(
      target = "noOfSurgeryClients",
      expression = "java(parseIntegerField(outcome.noOfSurgeryClients(), \"noOfSurgeryClients\"))")
  @Mapping(
      target = "noOfSuspects",
      expression = "java(parseIntegerField(outcome.noOfSuspects(), \"noOfSuspects\"))")
  @Mapping(
      target = "noOfPoliceStation",
      expression = "java(parseIntegerField(outcome.noOfPoliceStation(), \"noOfPoliceStation\"))")
  @Mapping(
      target = "numberOfMediationSessions",
      expression =
          "java(parseIntegerField(outcome.numberOfMediationSessions(), \"numberOfMediationSessions\"))")
  @Mapping(
      target = "mediationTime",
      expression = "java(parseIntegerField(outcome.mediationTime(), \"mediationTime\"))")
  @Mapping(
      target = "excessTravelCosts",
      expression = "java(parseBigDecimalField(outcome.excessTravelCosts(), \"excessTravelCosts\"))")
  BulkSubmissionOutcome toBulkSubmissionOutcome(XmlOutcome outcome);

  /**
   * Map to a {@link BulkSubmissionOutcome}.
   *
   * @param outcome the {@link CsvOutcome} to map.
   * @return a mapped {@link BulkSubmissionOutcome} object.
   */
  @Mapping(target = "caseStartDate", source = "caseStartDate")
  @Mapping(target = "clientDateOfBirth", source = "clientDateOfBirth")
  @Mapping(target = "workConcludedDate", source = "workConcludedDate")
  @Mapping(target = "transferDate", source = "transferDate")
  @Mapping(target = "surgeryDate", source = "surgeryDate")
  @Mapping(target = "repOrderDate", source = "repOrderDate")
  @Mapping(target = "client2DateOfBirth", source = "client2DateOfBirth")
  @Mapping(target = "medConcludedDate", source = "medConcludedDate")
  @Mapping(
      target = "vatIndicator",
      expression = "java(parseBooleanField(outcome.vatIndicator(), \"VAT Applicable\"))")
  @Mapping(
      target = "londonNonlondonRate",
      expression = "java(parseBooleanField(outcome.londonNonlondonRate(), \"London Rate\"))")
  @Mapping(
      target = "toleranceIndicator",
      expression =
          "java(parseBooleanField(outcome.toleranceIndicator(), \"Tolerance Applicable\"))")
  @Mapping(
      target = "legacyCase",
      expression = "java(parseBooleanField(outcome.legacyCase(), \"Legacy Case\"))")
  @Mapping(
      target = "postalApplAccp",
      expression =
          "java(parseBooleanField(outcome.postalApplAccp(), \"Postal Application Accepted\"))")
  @Mapping(
      target = "substantiveHearing",
      expression = "java(parseBooleanField(outcome.substantiveHearing(), \"Substantive Hearing\"))")
  @Mapping(
      target = "additionalTravelPayment",
      expression =
          "java(parseBooleanField(outcome.additionalTravelPayment(), \"Additional Travel Payment\"))")
  @Mapping(
      target = "clientLegallyAided",
      expression = "java(parseBooleanField(outcome.clientLegallyAided(), \"Is Legally Aided\"))")
  @Mapping(
      target = "client2PostalApplAccp",
      expression =
          "java(parseBooleanField(outcome.client2PostalApplAccp(), \"Client 2 Postal Application Accepted\"))")
  @Mapping(
      target = "dutySolicitor",
      expression = "java(parseBooleanField(outcome.dutySolicitor(), \"Duty Solicitor\"))")
  @Mapping(
      target = "nationalRefMechanismAdvice",
      expression = "java(parseBooleanField(outcome.nationalRefMechanismAdvice(), \"NRM Advice\"))")
  @Mapping(
      target = "ircSurgery",
      expression = "java(parseBooleanField(outcome.ircSurgery(), \"IRC Surgery\"))")
  @Mapping(
      target = "client2LegallyAided",
      expression =
          "java(parseBooleanField(outcome.client2LegallyAided(), \"Client 2 Legally Aided\"))")
  @Mapping(
      target = "eligibleClient",
      expression =
          "java(parseBooleanField(outcome.eligibleClientIndicator(), \"Eligible Client\"))")
  @Mapping(
      target = "youthCourt",
      expression = "java(parseBooleanField(outcome.youthCourt(), \"Youth Court\"))")
  @Mapping(
      target = "adviceTime",
      expression = "java(parseIntegerField(outcome.adviceTime(), \"adviceTime\"))")
  @Mapping(
      target = "travelTime",
      expression = "java(parseIntegerField(outcome.travelTime(), \"travelTime\"))")
  @Mapping(
      target = "waitingTime",
      expression = "java(parseIntegerField(outcome.waitingTime(), \"waitingTime\"))")
  @Mapping(
      target = "profitCost",
      expression = "java(parseBigDecimalField(outcome.profitCost(), \"profitCost\"))")
  @Mapping(
      target = "valueOfCosts",
      expression = "java(parseBigDecimalField(outcome.valueOfCosts(), \"valueOfCosts\"))")
  @Mapping(
      target = "disbursementsAmount",
      expression =
          "java(parseBigDecimalField(outcome.disbursementsAmount(), \"disbursementsAmount\"))")
  @Mapping(
      target = "counselCost",
      expression = "java(parseBigDecimalField(outcome.counselCost(), \"counselCost\"))")
  @Mapping(
      target = "disbursementsVat",
      expression = "java(parseBigDecimalField(outcome.disbursementsVat(), \"disbursementsVat\"))")
  @Mapping(
      target = "travelWaitingCosts",
      expression =
          "java(parseBigDecimalField(outcome.travelWaitingCosts(), \"travelWaitingCosts\"))")
  @Mapping(
      target = "travelCosts",
      expression = "java(parseBigDecimalField(outcome.travelCosts(), \"travelCosts\"))")
  @Mapping(
      target = "adjournedHearingFee",
      expression =
          "java(parseIntegerField(outcome.adjournedHearingFee(), \"adjournedHearingFee\"))")
  @Mapping(
      target = "hoInterview",
      expression = "java(parseIntegerField(outcome.hoInterview(), \"hoInterview\"))")
  @Mapping(
      target = "detentionTravelWaitingCosts",
      expression =
          "java(parseBigDecimalField(outcome.detentionTravelWaitingCosts(), \"detentionTravelWaitingCosts\"))")
  @Mapping(
      target = "medicalReportsClaimed",
      expression =
          "java(parseIntegerField(outcome.medicalReportsClaimed(), \"medicalReportsClaimed\"))")
  @Mapping(
      target = "desiAccRep",
      expression = "java(parseIntegerField(outcome.desiAccRep(), \"desiAccRep\"))")
  @Mapping(
      target = "noOfClients",
      expression = "java(parseIntegerField(outcome.noOfClients(), \"noOfClients\"))")
  @Mapping(
      target = "noOfSurgeryClients",
      expression = "java(parseIntegerField(outcome.noOfSurgeryClients(), \"noOfSurgeryClients\"))")
  @Mapping(
      target = "noOfSuspects",
      expression = "java(parseIntegerField(outcome.noOfSuspects(), \"noOfSuspects\"))")
  @Mapping(
      target = "noOfPoliceStation",
      expression = "java(parseIntegerField(outcome.noOfPoliceStation(), \"noOfPoliceStation\"))")
  @Mapping(
      target = "numberOfMediationSessions",
      expression =
          "java(parseIntegerField(outcome.numberOfMediationSessions(), \"numberOfMediationSessions\"))")
  @Mapping(
      target = "mediationTime",
      expression = "java(parseIntegerField(outcome.mediationTime(), \"mediationTime\"))")
  @Mapping(
      target = "excessTravelCosts",
      expression = "java(parseBigDecimalField(outcome.excessTravelCosts(), \"excessTravelCosts\"))")
  BulkSubmissionOutcome toBulkSubmissionOutcome(CsvOutcome outcome);

  /**
   * Map to a {@link BulkSubmissionMatterStart}.
   *
   * @param csvMatterStarts the {@link CsvMatterStarts} to map.
   * @return a mapped {@link BulkSubmissionMatterStart} object.
   */
  @Mapping(
      target = "numberOfMatterStarts",
      expression =
          "java(parseIntegerField(csvMatterStarts.numberOfMatterStarts(), \"numberOfMatterStarts\"))")
  BulkSubmissionMatterStart toBulkSubmissionMatterStarts(CsvMatterStarts csvMatterStarts);

  /**
   * Map to a {@link BulkSubmissionMatterStart}.
   *
   * @param xmlMatterStarts the {@link XmlMatterStarts} to map.
   * @return a mapped {@link BulkSubmissionMatterStart} object.
   */
  BulkSubmissionMatterStart toBulkSubmissionMatterStarts(XmlMatterStarts xmlMatterStarts);

  /**
   * Map to a {@link java.lang.Integer}.
   *
   * @param value the value to map.
   * @param fieldName the name of the field being mapped.
   * @return a BigDecimal representation of the outcome field.
   */
  default Integer parseIntegerField(String value, String fieldName) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException ex) {
      throw new BulkSubmissionFieldConversionException(fieldName, value, ex);
    }
  }

  /**
   * Map to a {@link BigDecimal}.
   *
   * @param value the value to map.
   * @param fieldName the name of the field being mapped.
   * @return a BigDecimal representation of the outcome field.
   */
  default BigDecimal parseBigDecimalField(String value, String fieldName) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException ex) {
      throw new BulkSubmissionFieldConversionException(fieldName, value, ex);
    }
  }

  /**
   * Maps string values "Y" or "N" to corresponding boolean values. Empty or null strings return
   * null.
   *
   * @param value the string value to convert ("Y" or "N")
   * @param fieldName the name of the field being mapped (used for error reporting)
   * @return true for "Y", false for "N", null for empty/null strings
   * @throws BulkSubmissionFieldConversionException if value is neither "Y" nor "N"
   */
  default Boolean parseBooleanField(String value, String fieldName) {
    if (!StringUtils.hasText(value)) {
      return null;
    }

    return switch (value) {
      case "Y" -> true;
      case "N" -> false;
      default -> throw new BulkSubmissionFieldConversionException(fieldName, value, true);
    };
  }

  /**
   * Maps XML immigration CLR data to a list of field maps. Each map in the resulting list
   * represents field name/value pairs from an immigration CLR record.
   *
   * @param immigrationClrList the list of XML immigration CLR records to map
   * @return a list of maps containing the field name/value pairs, or empty list if input is null
   */
  default List<Map<String, String>> mapImmigrationClrData(
      List<XmlImmigrationClr> immigrationClrList) {
    if (immigrationClrList == null) {
      return new ArrayList<>();
    }
    return immigrationClrList.stream().map(XmlImmigrationClr::fields).toList();
  }
}
