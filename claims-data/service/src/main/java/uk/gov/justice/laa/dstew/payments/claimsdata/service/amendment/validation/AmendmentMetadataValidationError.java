package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import lombok.Getter;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;

/**
 * Catalogue of amendment metadata validation errors raised by {@link ClaimReasonValidationStep}.
 *
 * <p>Each value pairs a stable, machine-readable {@code code} (the enum name) with the technical
 * message text (for internal logs / the {@code technical_message} field) and the user-facing
 * display message template (for the {@code display_message} field). Templates that contain {@code
 * %s} placeholders are filled via {@link #formatDisplayMessage(Object...)} with the submitted
 * value(s).
 *
 * <p>The enum name is persisted as the validation message {@code source} so consumers retain the
 * stable error code.
 */
@Getter
public enum AmendmentMetadataValidationError {

  // Requested By
  INVALID_REQUESTED_BY_MISSING("Requested By code is absent", "Requested By is required"),
  INVALID_REQUESTED_BY_UNKNOWN(
      "Requested By code is not present in the Reference Data lookup",
      "Requested By '%s' is not a recognised value"),
  INVALID_REQUESTED_BY_INACTIVE(
      "Requested By code is present in the lookup but currently inactive",
      "Requested By '%s' is no longer in use"),
  INVALID_REQUESTED_BY_NOT_A_CODE(
      "Requested By value is a display label rather than a code",
      "Requested By must be supplied as a code, not a display label"),

  // Amendment Reason
  INVALID_AMENDMENT_REASON_MISSING(
      "Amendment Reason code is absent", "Amendment Reason is required"),
  INVALID_AMENDMENT_REASON_UNKNOWN(
      "Amendment Reason code is not present in the Reference Data lookup",
      "Amendment Reason '%s' is not a recognised value"),
  INVALID_AMENDMENT_REASON_INACTIVE(
      "Amendment Reason code is present in the lookup but currently inactive",
      "Amendment Reason '%s' is no longer in use"),
  INVALID_AMENDMENT_REASON_NOT_A_CODE(
      "Amendment Reason value is a display label rather than a code",
      "Amendment Reason must be supplied as a code, not a display label"),
  INVALID_AMENDMENT_REASON_FOR_REQUESTED_BY(
      "Amendment Reason code exists but is not valid for the submitted Requested By code",
      "Amendment Reason '%s' is not valid for Requested By '%s'"),

  // Submitting user
  INVALID_USER_IDENTIFIER_FORMAT(
      "Submitting user's Entra UUID is not a structurally valid UUID",
      "The user identifier must be a valid UUID"),

  // Controlled technical failure: reference data could not be read, so nothing is saved
  TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA(
      "Required amendment metadata reference data was unavailable at submit time",
      "A technical error occurred, please try again after some time");

  private final String technicalMessage;
  private final String displayMessageTemplate;

  AmendmentMetadataValidationError(String technicalMessage, String displayMessageTemplate) {
    this.technicalMessage = technicalMessage;
    this.displayMessageTemplate = displayMessageTemplate;
  }

  /**
   * Returns the stable error code (the enum name).
   *
   * @return the error code
   */
  public String code() {
    return name();
  }

  /**
   * Formats the display message, substituting any {@code %s} placeholders with the supplied
   * arguments. When the template has no placeholders the arguments are ignored.
   *
   * @param args the values to substitute into the template
   * @return the formatted display message
   */
  public String formatDisplayMessage(Object... args) {
    if (args == null || args.length == 0) {
      return displayMessageTemplate;
    }
    return String.format(displayMessageTemplate, args);
  }

  /**
   * Builds an {@link ValidationMessageType#ERROR} validation issue for this error, carrying the
   * stable code in {@code source}, the formatted user-facing text in {@code displayMessage} and the
   * internal text in {@code technicalMessage}.
   *
   * @param args the values to substitute into the display message template
   * @return the validation issue
   */
  public ValidationMessagePatch toValidationIssue(Object... args) {
    return new ValidationMessagePatch()
        .type(ValidationMessageType.ERROR)
        .source(code())
        .displayMessage(formatDisplayMessage(args))
        .technicalMessage(technicalMessage);
  }
}
