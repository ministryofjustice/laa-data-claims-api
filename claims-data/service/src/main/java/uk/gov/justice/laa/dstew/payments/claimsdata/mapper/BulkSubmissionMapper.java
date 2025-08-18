package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvMatterStarts;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvOffice;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvSchedule;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlOffice;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlSchedule;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlSubmission;

/** Mapping interface for the mapping of bulk submission objects. */
@Mapper(componentModel = "spring", uses = GlobalStringMapper.class)
public interface BulkSubmissionMapper {
  /**
   * Maps the given {@code FileSubmission} to a {@code BulkSubmissionDetails}.
   *
   * @param submission the java representation of the bulk submission file
   * @return the API model for the bulk submission.
   */
  default BulkSubmissionDetails toBulkSubmissionDetails(FileSubmission submission) {
    if (submission instanceof CsvSubmission csvSubmission) {
      return toBulkSubmissionDetails(csvSubmission);
    } else if (submission instanceof XmlSubmission xmlSubmission) {
      return toBulkSubmissionDetails(xmlSubmission);
    } else {
      throw new IllegalArgumentException("Unsupported submission type: " + submission.getClass());
    }
  }

  /**
   * Maps the given {@code XmlSubmission} to a {@code BulkSubmissionDetails}.
   *
   * @param submission the java representation of the xml bulk submission file
   * @return the API model for the bulk submission.
   */
  @Mapping(target = "schedule", source = "office.schedule")
  @Mapping(target = "outcomes", source = "office.schedule.outcomes")
  @Mapping(target = "matterStarts", expression = "java(new ArrayList<>())")
  BulkSubmissionDetails toBulkSubmissionDetails(XmlSubmission submission);

  /**
   * Maps the given {@code CsvSubmission} to a {@code BulkSubmissionDetails}.
   *
   * @param submission the java representation of the csv bulk submission file
   * @return the API model for the bulk submission.
   */
  @Mapping(target = "matterStarts", source = "matterStarts",
      defaultExpression = "java(new ArrayList<>())")
  BulkSubmissionDetails toBulkSubmissionDetails(CsvSubmission submission);

  /**
   * Map to a {@link BulkSubmissionOffice}.
   *
   * @param office the {@link XmlOffice} to map.
   * @return a mapped {@link BulkSubmissionOffice} object.
   */
  BulkSubmissionOffice toBulkSubmissionOffice(XmlOffice office);

  /**
   * Map to a {@link BulkSubmissionOffice}.
   *
   * @param office the {@link CsvOffice} to map.
   * @return a mapped {@link BulkSubmissionOffice} object.
   */
  BulkSubmissionOffice toBulkSubmissionOffice(CsvOffice office);

  /**
   * Map to a {@link BulkSubmissionSchedule}.
   *
   * @param schedule the {@link XmlSchedule} to map.
   * @return a mapped {@link BulkSubmissionSchedule} object.
   */
  BulkSubmissionSchedule toBulkSubmissionSchedule(XmlSchedule schedule);

  /**
   * Map to a {@link BulkSubmissionSchedule}.
   *
   * @param schedule the {@link CsvSchedule} to map.
   * @return a mapped {@link BulkSubmissionSchedule} object.
   */
  BulkSubmissionSchedule toBulkSubmissionSchedule(CsvSchedule schedule);

  /**
   * Map to a {@link BulkSubmissionOutcome}.
   *
   * @param outcome the {@link XmlOutcome} to map.
   * @return a mapped {@link BulkSubmissionOutcome} object.
   */
  @Mapping(target = "caseStartDate", source = "caseStartDate")
  @Mapping(target = "clientDateOfBirth", source = "clientDateOfBirth")
  @Mapping(target = "workConcludedDate", source = "workConcludedDate")
  @Mapping(
      target = "transferDate", source = "transferDate")
  @Mapping(
      target = "surgeryDate", source = "surgeryDate")
  @Mapping(
      target = "repOrderDate", source = "repOrderDate")
  @Mapping(target = "client2DateOfBirth", source = "client2DateOfBirth")
  @Mapping(target = "medConcludedDate", source = "medConcludedDate")
  @Mapping(
      target = "vatIndicator", source = "vatIndicator", qualifiedByName = "outcomeFieldToBoolean")
  @Mapping(target = "londonNonlondonRate", source = "londonNonlondonRate",
      qualifiedByName = "outcomeFieldToBoolean")
  @Mapping(target = "toleranceIndicator", source = "toleranceIndicator",
      qualifiedByName = "outcomeFieldToBoolean")
  @Mapping(target = "legacyCase", source = "legacyCase", qualifiedByName = "outcomeFieldToBoolean")
  @Mapping(target = "postalApplAccp", source = "postalApplAccp",
      qualifiedByName = "outcomeFieldToBoolean")
  @Mapping(target = "substantiveHearing", source = "substantiveHearing",
      qualifiedByName = "outcomeFieldToBoolean")
  @Mapping(target = "additionalTravelPayment", source = "additionalTravelPayment",
      qualifiedByName = "outcomeFieldToBoolean")
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
  @Mapping(
      target = "transferDate", source = "transferDate")
  @Mapping(
      target = "surgeryDate", source = "surgeryDate")
  @Mapping(
      target = "repOrderDate", source = "repOrderDate")
  @Mapping(target = "client2DateOfBirth", source = "client2DateOfBirth")
  @Mapping(target = "medConcludedDate", source = "medConcludedDate")
  @Mapping(
      target = "vatIndicator", source = "vatIndicator", qualifiedByName = "outcomeFieldToBoolean")
  @Mapping(target = "londonNonlondonRate", source = "londonNonlondonRate",
      qualifiedByName = "outcomeFieldToBoolean")
  @Mapping(target = "toleranceIndicator", source = "toleranceIndicator",
      qualifiedByName = "outcomeFieldToBoolean")
  @Mapping(target = "legacyCase", source = "legacyCase", qualifiedByName = "outcomeFieldToBoolean")
  @Mapping(target = "postalApplAccp", source = "postalApplAccp",
      qualifiedByName = "outcomeFieldToBoolean")
  @Mapping(target = "substantiveHearing", source = "substantiveHearing",
      qualifiedByName = "outcomeFieldToBoolean")
  @Mapping(target = "additionalTravelPayment", source = "additionalTravelPayment",
      qualifiedByName = "outcomeFieldToBoolean")
  BulkSubmissionOutcome toBulkSubmissionOutcome(CsvOutcome outcome);

  /**
   * Map to a {@link BulkSubmissionMatterStart}.
   *
   * @param csvMatterStarts the {@link CsvMatterStarts} to map.
   * @return a mapped {@link BulkSubmissionMatterStart} object.
   */
  BulkSubmissionMatterStart toBulkSubmissionMatterStarts(CsvMatterStarts csvMatterStarts);

  /**
   * Map to a {@link Boolean}.
   *
   * @param outcomeField the outcome field to map.
   * @return a boolean representation of the outcome field.
   */
  @Named("outcomeFieldToBoolean")
  default Boolean toBoolean(String outcomeField) {
    if (outcomeField == null) {
      return null;
    }
    return "Y".equals(outcomeField);
  }
}
