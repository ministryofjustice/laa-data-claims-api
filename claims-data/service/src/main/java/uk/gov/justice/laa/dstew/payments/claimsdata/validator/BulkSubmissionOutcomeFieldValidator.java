package uk.gov.justice.laa.dstew.payments.claimsdata.validator;

import java.util.Set;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFileReadException;

/** Validates outcome field names before CSV or XML mapping. */
public final class BulkSubmissionOutcomeFieldValidator {

  private static final Set<String> INQUEST_FIELDS =
      Set.of(
          "INQ_CLIENT_MEANS_TESTED",
          "DECEASED_FIRST_NAME",
          "DECEASED_SURNAME",
          "DATE_OF_DEATH",
          "INQUEST_REF",
          "GOV_DEPT_1",
          "GOV_DEPT_2",
          "GOV_DEPT_3",
          "GOV_DEPT_4",
          "GOV_DEPT_5",
          "GOV_DEPT_6",
          "GOV_DEPT_7",
          "GOV_DEPT_8",
          "GOV_DEPT_9",
          "GOV_DEPT_10");

  private BulkSubmissionOutcomeFieldValidator() {}

  /**
   * Rejects inquest fields when the feature is disabled.
   *
   * @param field the raw outcome field name
   * @param inquestsEnabled whether inquest fields are enabled
   */
  public static void validate(String field, boolean inquestsEnabled) {
    if (!inquestsEnabled && INQUEST_FIELDS.contains(field)) {
      throw new BulkSubmissionFileReadException(
          "The file contains an unrecognised field %s. Correct or remove the field and try again."
              .formatted(field));
    }
  }
}
