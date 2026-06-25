package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.springframework.http.HttpStatus;

/**
 * Immutable description of a single claim amendment validation failure, returned by a validation
 * step.
 *
 * <p>The error's behaviour - its severity (and hence whether it is fatal) and the HTTP status it
 * maps to - is defined once on its {@link ClaimAmendmentValidationCode} (the catalogue) and
 * surfaced here for convenience; this type only adds the concrete, formatted {@link #getMessage()
 * message} for this occurrence. Create instances with {@link #of(ClaimAmendmentValidationCode,
 * Object...)} so the message is always derived from the code's template.
 *
 * <p>Error-handling contract: a <b>fatal</b> error halts the flow immediately (no later step runs);
 * <b>non-fatal</b> errors are collected so the caller can be shown every failure at once. Any
 * non-empty set of errors fails the amendment and nothing is persisted.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ClaimAmendmentValidationError {

  ClaimAmendmentValidationCode code;
  String message;

  /**
   * Creates an error for the given code, formatting the code's message template with the supplied
   * runtime values.
   *
   * @param code the error code - the source of truth for fatality, HTTP status and message wording
   * @param messageArgs values for the code's message-template placeholders, if any
   * @return the validation error
   */
  public static ClaimAmendmentValidationError of(
      ClaimAmendmentValidationCode code, Object... messageArgs) {
    return new ClaimAmendmentValidationError(
        code, code.getMessageTemplate().formatted(messageArgs));
  }

  /**
   * The severity of this error, as defined by its {@link ClaimAmendmentValidationCode}.
   *
   * @return the severity
   */
  public ValidationSeverity getSeverity() {
    return code.getSeverity();
  }

  /**
   * Whether this error is fatal, as defined by its {@link ClaimAmendmentValidationCode}.
   *
   * @return {@code true} if fatal
   */
  public boolean isFatal() {
    return code.isFatal();
  }

  /**
   * The HTTP status for this error, as defined by its {@link ClaimAmendmentValidationCode}.
   *
   * @return the HTTP status
   */
  public HttpStatus getHttpStatus() {
    return code.getHttpStatus();
  }
}
