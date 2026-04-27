package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request to a validation-message endpoint is malformed – for example when an
 * unrecognised sort field is supplied.
 *
 * <p>Extends {@link ClaimsDataException} so the global exception handler automatically responds
 * with a {@link HttpStatus#BAD_REQUEST 400 Bad Request}.
 */
public class ValidationMessageBadRequestException extends ClaimsDataException {
  public ValidationMessageBadRequestException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
