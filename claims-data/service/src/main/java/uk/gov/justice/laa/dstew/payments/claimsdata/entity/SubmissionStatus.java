package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

/**
 * Enumeration of statuses for a submission.
 */
public enum SubmissionStatus {
  CREATED,
  READY_FOR_VALIDATION,
  VALIDATION_IN_PROGRESS,
  VALIDATION_SUCCEEDED,
  VALIDATION_FAILED,
  REPLACED
}
