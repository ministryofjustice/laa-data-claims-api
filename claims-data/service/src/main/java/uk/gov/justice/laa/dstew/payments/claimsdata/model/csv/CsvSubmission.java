package uk.gov.justice.laa.dstew.payments.claimsdata.model.csv;

import java.util.List;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileSubmission;

/**
 * Record holding bulk submission details sourced from a CSV file.
 *
 * @param office the office submitting the claim
 * @param schedule the schedule details
 * @param outcomes the submission outcomes
 * @param matterStarts the submission matter starts
 */
public record CsvSubmission(CsvOffice office, CsvSchedule schedule, List<CsvOutcome> outcomes,
    List<CsvMatterStarts> matterStarts) implements FileSubmission {}
