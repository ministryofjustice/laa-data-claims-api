package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Exception representing a conflicting live bulk submission for the same office/area-of-law/period.
 *
 * <p>Contains the conflicting office code, area of law and submission period as metadata so the
 * exception handler can expose these values in the Problem Detail response.
 */
@Getter
public class BulkSubmissionPeriodConflictException extends ClaimsDataException {

  private final String officeCode;
  private final String areaOfLaw;
  private final String submissionPeriod;

  /**
   * Construct a new exception with the specified detail message and metadata.
   *
   * @param message the detail message
   * @param officeCode the office code of the conflicting submission
   * @param areaOfLaw the area of law of the conflicting submission
   * @param submissionPeriod the submission period of the conflicting submission
   */
  public BulkSubmissionPeriodConflictException(
      String message, String officeCode, String areaOfLaw, String submissionPeriod) {
    super(message, HttpStatus.CONFLICT);
    this.officeCode = officeCode;
    this.areaOfLaw = areaOfLaw;
    this.submissionPeriod = submissionPeriod;
  }
}
