package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationIssue;

/**
 * Immutable description of a single claim amendment validation failure, returned by a validation
 * step.
 *
 * <p>The instance carries the concrete, user-facing {@link #message} for this occurrence and the
 * operational properties the amendment flow uses to decide how to proceed: {@link #severity}, the
 * HTTP status to return and whether the error is {@link #isFatal fatal}.
 *
 * <p>There are two ways to create an instance:
 *
 * <ul>
 *   <li>{@link #of(ClaimAmendmentValidationCode, Object...)} — create an error from the project's
 *       canonical {@link ClaimAmendmentValidationCode} catalogue. This preserves the code's
 *       predefined severity, HTTP status and fatality and formats the message using the code's
 *       template.
 *   <li>{@link #from(ValidationIssue)} — a pass-through factory used when the error originates from
 *       an external validation library; the returned instance preserves the issue's code and
 *       message and assigns a sensible default severity/HTTP status for amendment handling.
 * </ul>
 *
 * <p>Error-handling contract: a <b>fatal</b> error halts the flow immediately (no later step runs);
 * <b>non-fatal</b> errors are collected so the caller can be shown every failure at once. Any
 * non-empty set of errors fails the amendment and nothing is persisted.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ClaimAmendmentValidationError {

  String code;
  String message;
  ValidationSeverity severity;
  HttpStatus httpStatus;
  boolean isFatal;

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
        code.toString(),
        code.getMessageTemplate().formatted(messageArgs),
        code.getSeverity(),
        code.getHttpStatus(),
        code.isFatal());
  }

  /**
   * Creates an amendment error from an external {@link ValidationIssue} returned by the shared
   * validation library.
   *
   * <p>This is a pass-through mapping: the issue's {@code code} and formatted {@code message} are
   * preserved as-is. Because external issues don't map directly to the project's closed catalogue
   * of amendment codes, a conservative default is applied for severity and HTTP status so the
   * amendment flow can handle the finding consistently.
   *
   * @param validationIssue the external validation issue (may not be {@code null})
   * @return a {@link ClaimAmendmentValidationError} representing the issue inside the amendment
   *     flow
   */
  public static ClaimAmendmentValidationError from(ValidationIssue validationIssue) {
    return new ClaimAmendmentValidationError(
        validationIssue.getCode(),
        validationIssue.getMessage(),
        ValidationSeverity.ERROR,
        HttpStatus.BAD_REQUEST,
        false);
  }
}
