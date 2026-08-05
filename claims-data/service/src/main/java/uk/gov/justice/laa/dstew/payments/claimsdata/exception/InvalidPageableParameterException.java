package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import org.springframework.http.HttpStatus;

/** Exception thrown when pageable request parameters are invalid. Mapped to a 400 Bad Request. */
public class InvalidPageableParameterException extends ClaimsDataException {

  public InvalidPageableParameterException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }

  public InvalidPageableParameterException(String message, Throwable cause) {
    super(message, HttpStatus.BAD_REQUEST, cause);
  }
}
