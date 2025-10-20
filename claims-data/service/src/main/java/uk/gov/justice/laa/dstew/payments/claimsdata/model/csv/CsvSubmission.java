package uk.gov.justice.laa.dstew.payments.claimsdata.model.csv;

import java.util.List;
import java.util.Map;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileSubmission;

/**
 * Record holding bulk submission details sourced from a CSV file.
 *
 * @param office the office submitting the claim
 * @param schedule the schedule details
 * @param outcomes the submission outcomes
 * @param matterStarts the submission matter starts
 * @param immigrationClr the immigration CLR rows captured as key/value pairs
 */
public record CsvSubmission(
    CsvOffice office,
    CsvSchedule schedule,
    List<CsvOutcome> outcomes,
    List<CsvMatterStarts> matterStarts,
    List<Map<String, String>> immigrationClr)
    implements FileSubmission {}
