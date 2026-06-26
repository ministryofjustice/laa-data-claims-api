package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import org.springframework.http.HttpStatus;

/**
 * Central catalogue of every claim amendment validation error, together with the properties that
 * determine its effect on the application user: whether it is {@link #isFatal() fatal} (a
 * show-stopper that halts the flow immediately), the {@link #getHttpStatus() HTTP status} returned
 * to the caller, and the user-facing message.
 *
 * <p>This is the single source of truth for amendment error behaviour and is intended to be
 * reviewable by non-developers (e.g. BAs): each constant declares its effect in one place. These
 * properties are static - an error keeps them wherever it is raised. Message templates may contain
 * {@link String#formatted(Object...) format placeholders} that are filled with runtime values when
 * the error is raised (see {@code ClaimAmendmentValidationError.of}).
 */
public enum ClaimAmendmentValidationCode {

  /** The claim is voided and therefore cannot be amended. */
  INVALID_CLAIM_VERSION_CONFLICT(
      ValidationSeverity.FATAL, HttpStatus.CONFLICT, "Claim Version conflict exists"),

  /** The claim is voided and therefore cannot be amended. */
  INVALID_VOIDED_CLAIM_NOT_AMENDABLE(
      ValidationSeverity.FATAL, HttpStatus.BAD_REQUEST, "A voided claim cannot be amended."),

  /**
   * The claim is in a non-amendable state - any {@code claim.status} other than {@code VALID} that
   * is not voided.
   */
  INVALID_CLAIM_STATE_NOT_AMENDABLE(
      ValidationSeverity.FATAL,
      HttpStatus.BAD_REQUEST,
      "Claim status %s is not amendable; only claims with status %s can be amended.");

  private final ValidationSeverity severity;
  private final HttpStatus httpStatus;
  private final String messageTemplate;

  ClaimAmendmentValidationCode(
      ValidationSeverity severity, HttpStatus httpStatus, String messageTemplate) {
    this.severity = severity;
    this.httpStatus = httpStatus;
    this.messageTemplate = messageTemplate;
  }

  /**
   * The severity of this error, which determines whether it is fatal.
   *
   * @return the severity
   */
  public ValidationSeverity getSeverity() {
    return severity;
  }

  /**
   * Whether this error is fatal - a show-stopper that halts the amendment flow immediately, so no
   * later step runs and nothing is saved. Derived from {@link #getSeverity() severity}.
   *
   * @return {@code true} if fatal
   */
  public boolean isFatal() {
    return severity.isFatal();
  }

  /**
   * The HTTP status returned to the caller for this error.
   *
   * @return the HTTP status
   */
  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  /**
   * The user-facing message template; may contain {@link String#formatted(Object...)} placeholders
   * that are filled with runtime values when the error is raised.
   *
   * @return the message template
   */
  public String getMessageTemplate() {
    return messageTemplate;
  }
}
