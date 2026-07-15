package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import java.util.Objects;
import lombok.Getter;
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
@Getter
public enum ClaimAmendmentValidationCode {

  /** The Amendments feature is disabled by configuration. */
  INVALID_AMENDMENTS_FEATURE_DISABLED(
      ValidationSeverity.FATAL,
      HttpStatus.SERVICE_UNAVAILABLE,
      "Amendments are not currently enabled.",
      "Amendments feature flag (laa.claims.api.amendments.enabled) is disabled"),

  /** The claim has a null version number so cannot be amended. */
  INVALID_NULL_VERSION(
      ValidationSeverity.FATAL, HttpStatus.BAD_REQUEST, "Claim Version is null", null),

  /** The claim has a stale version number so cannot be amended. */
  INVALID_CLAIM_VERSION_CONFLICT(
      ValidationSeverity.FATAL, HttpStatus.CONFLICT, "Claim Version conflict exists", null),

  /** The claim is voided and therefore cannot be amended. */
  INVALID_VOIDED_CLAIM_NOT_AMENDABLE(
      ValidationSeverity.FATAL, HttpStatus.BAD_REQUEST, "A voided claim cannot be amended.", null),

  /**
   * The claim is in a non-amendable state - any {@code claim.status} other than {@code VALID} that
   * is not voided.
   */
  INVALID_CLAIM_STATE_NOT_AMENDABLE(
      ValidationSeverity.FATAL,
      HttpStatus.BAD_REQUEST,
      "Claim status %s is not amendable; only claims with status %s can be amended.",
      null),

  // ----- Amendment metadata: Requested By (DSTEW-1765) -----

  /** Requested By code was not supplied. */
  INVALID_REQUESTED_BY_MISSING(
      ValidationSeverity.ERROR,
      HttpStatus.BAD_REQUEST,
      "Requested By is required",
      "Requested By code is absent"),

  /** Requested By code is not present in the reference-data lookup. */
  INVALID_REQUESTED_BY_UNKNOWN(
      ValidationSeverity.ERROR,
      HttpStatus.BAD_REQUEST,
      "Requested By '%s' is not a recognised value",
      "Requested By code is not present in the Reference Data lookup"),

  /** Requested By code exists in the lookup but is currently inactive. */
  INVALID_REQUESTED_BY_INACTIVE(
      ValidationSeverity.ERROR,
      HttpStatus.BAD_REQUEST,
      "Requested By '%s' is no longer in use",
      "Requested By code is present in the lookup but currently inactive"),

  /** Requested By value is a display label rather than a stable code. */
  INVALID_REQUESTED_BY_NOT_A_CODE(
      ValidationSeverity.ERROR,
      HttpStatus.BAD_REQUEST,
      "Requested By must be supplied as a code, not a display label",
      "Requested By value is a display label rather than a code"),

  // ----- Amendment metadata: Amendment Reason (DSTEW-1765) -----

  /** Amendment Reason code was not supplied. */
  INVALID_AMENDMENT_REASON_MISSING(
      ValidationSeverity.ERROR,
      HttpStatus.BAD_REQUEST,
      "Amendment Reason is required",
      "Amendment Reason code is absent"),

  /** Amendment Reason code is not present in the reference-data lookup. */
  INVALID_AMENDMENT_REASON_UNKNOWN(
      ValidationSeverity.ERROR,
      HttpStatus.BAD_REQUEST,
      "Amendment Reason '%s' is not a recognised value",
      "Amendment Reason code is not present in the Reference Data lookup"),

  /** Amendment Reason code exists in the lookup but is currently inactive. */
  INVALID_AMENDMENT_REASON_INACTIVE(
      ValidationSeverity.ERROR,
      HttpStatus.BAD_REQUEST,
      "Amendment Reason '%s' is no longer in use",
      "Amendment Reason code is present in the lookup but currently inactive"),

  /** Amendment Reason value is a display label rather than a stable code. */
  INVALID_AMENDMENT_REASON_NOT_A_CODE(
      ValidationSeverity.ERROR,
      HttpStatus.BAD_REQUEST,
      "Amendment Reason must be supplied as a code, not a display label",
      "Amendment Reason value is a display label rather than a code"),

  /** Amendment Reason code exists but is not valid for the submitted Requested By code. */
  INVALID_AMENDMENT_REASON_FOR_REQUESTED_BY(
      ValidationSeverity.ERROR,
      HttpStatus.BAD_REQUEST,
      "Amendment Reason '%s' is not valid for Requested By '%s'",
      "Amendment Reason code exists but is not valid for the submitted Requested By code"),

  // ----- Amendment metadata: submitting user (DSTEW-1765) -----

  /** The submitting user's Entra identifier was not supplied. */
  INVALID_USER_IDENTIFIER_MISSING(
      ValidationSeverity.ERROR,
      HttpStatus.BAD_REQUEST,
      "The user identifier is required",
      "Submitting user's Entra UUID is absent"),

  /** The submitting user's Entra identifier is not a structurally valid UUID. */
  INVALID_USER_IDENTIFIER_FORMAT(
      ValidationSeverity.ERROR,
      HttpStatus.BAD_REQUEST,
      "The user identifier must be a valid UUID",
      "Submitting user's Entra UUID is not a structurally valid UUID"),

  // ----- Amendment metadata: technical failures (DSTEW-1765) -----

  /** The governed amendment metadata reference data was unavailable at submit time. */
  TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA(
      ValidationSeverity.FATAL,
      HttpStatus.SERVICE_UNAVAILABLE,
      "A technical error occurred, please try again after some time",
      "Required amendment metadata reference data was unavailable at submit time"),

  // ----- Fee code Area of Law gate (DSTEW-1768) -----

  /**
   * A fee code change targets a code in a different Area of Law to the claim. This is a terminal
   * rejection: a fee code may only be changed to another fee code within the same Area of Law.
   */
  INVALID_FEE_CODE_AREA_OF_LAW_CHANGE(
      ValidationSeverity.FATAL,
      HttpStatus.BAD_REQUEST,
      "Fee code cannot be changed to '%s' because it belongs to a different Area of Law (%s); "
          + "the claim's Area of Law is %s.",
      "Fee code change targets a fee code in a different Area of Law");

  /** The severity of this error, which determines whether it is fatal. */
  private final ValidationSeverity severity;

  /** The HTTP status returned to the caller for this error. */
  private final HttpStatus httpStatus;

  /**
   * The user-facing message template; may contain placeholders that are filled with runtime values
   * when the error is raised.
   */
  private final String messageTemplate;

  /**
   * The internal, developer-facing technical description of this error, used for diagnostics and
   * logging. May be for codes that have no dedicated technical message.
   */
  private final String technicalMessage;

  ClaimAmendmentValidationCode(
      ValidationSeverity severity,
      HttpStatus httpStatus,
      String messageTemplate,
      String technicalMessage) {
    this.severity = Objects.requireNonNull(severity, "severity");
    this.httpStatus = httpStatus;
    this.messageTemplate = messageTemplate;
    this.technicalMessage = technicalMessage;
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
}
