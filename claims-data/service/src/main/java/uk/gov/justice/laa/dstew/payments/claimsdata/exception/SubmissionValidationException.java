package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import java.util.List;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationIssue;

/**
 * Thrown when a submission fails pre-persistence validation.
 *
 * <p>Results in a {@code 400 Bad Request} response. The {@link ValidationIssue} list is carried on
 * the exception so that {@link DataClaimsExceptionHandler} can surface it as a structured property
 * inside the RFC 9457 Problem Detail response body.
 */
@Getter
public class SubmissionValidationException extends ClaimsDataException {

  private final List<ValidationIssue> issues;

  /**
   * Construct a new exception with the given detail message and validation issues.
   *
   * @param message human-readable summary (used as the Problem Detail {@code detail} field)
   * @param issues the list of individual validation issues returned by the validation service
   */
  public SubmissionValidationException(String message, List<ValidationIssue> issues) {
    super(message, HttpStatus.BAD_REQUEST);
    this.issues = issues != null ? List.copyOf(issues) : List.of();
  }
}
