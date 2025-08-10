package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

/** Statuses representing the state of a bulk submission. */
public enum BulkSubmissionStatus {
  READY_FOR_PARSING,
  PARSING_COMPLETED,
  PARSING_FAILED,
  VALIDATION_FAILED,
  REPLACED
}
