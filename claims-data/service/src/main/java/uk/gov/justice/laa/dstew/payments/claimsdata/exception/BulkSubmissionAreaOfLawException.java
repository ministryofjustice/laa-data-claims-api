package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;

/** Exception thrown when an unsupported area of law is supplied for a bulk submission. */
public class BulkSubmissionAreaOfLawException extends ClaimsDataException {

  /**
   * Constructs a new BulkSubmissionAreaOfLawException with a predefined message and HTTP status.
   */
  public BulkSubmissionAreaOfLawException() {
    super("Area of law must be Mediation, Crime Lower or Legal Help", HttpStatus.BAD_REQUEST);
  }
}
