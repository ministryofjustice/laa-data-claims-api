package uk.gov.justice.laa.dstew.payments.claimsdata.model.csv;

/**
 * Record holding bulk submission schedule details.
 *
 * @param submissionPeriod the submission period
 * @param areaOfLaw the area of law for the submission
 * @param scheduleNum the submission schedule number
 */
public record CsvSchedule(String submissionPeriod, String areaOfLaw, String scheduleNum) {}
