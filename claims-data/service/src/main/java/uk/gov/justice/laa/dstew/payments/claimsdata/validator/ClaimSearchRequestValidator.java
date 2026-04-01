package uk.gov.justice.laa.dstew.payments.claimsdata.validator;

import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.ClaimSearchRequest;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimBadRequestException;

/**
 * Validator component responsible for validating {@link ClaimSearchRequest} instances.
 *
 * <p>This class performs light-weight, pre-flight validations on search requests submitted to
 * listing endpoints (for example, {@code GET /api/v2/claims}). It intentionally focuses on
 * validating input contract requirements and does not perform any persistence or business rule
 * validations which belong to other services.
 *
 * <p>Validation rules implemented:
 *
 * <ul>
 *   <li>The request object itself must not be {@code null}.
 *   <li>The {@code officeCode} must be present and contain at least one non-whitespace character.
 *   <li>If {@code caseReferenceNumber} is supplied (non-null), it will be trimmed and then:
 *       <ul>
 *         <li>an all-whitespace/empty value is treated as absent (i.e. no validation error);
 *         <li>otherwise the trimmed value must be at least {@link #MIN_CASE_REFERENCE_LENGTH}
 *             characters long.
 *       </ul>
 * </ul>
 *
 * <p>When a validation rule is violated a {@link ClaimBadRequestException} is thrown with a
 * descriptive message. The messages used by this class are defined as constants so callers can
 * assert against or reuse them if required.
 */
@Component
@RequiredArgsConstructor
public class ClaimSearchRequestValidator {

  /** Minimum permitted length for a (trimmed) case reference number when one is supplied. */
  public static final int MIN_CASE_REFERENCE_LENGTH = 3;

  /** Maximum permitted length for a (trimmed) case reference number when one is supplied. */
  public static final int MAX_CASE_REFERENCE_LENGTH = 30;

  public static final String MISSING_SEARCH_REQUEST = "Missing search request";
  public static final String MISSING_OFFICE_CODE = "Missing office code";
  public static final String CASE_REFERENCE_TOO_SHORT =
      "case_reference_number must be at least %d characters";
  public static final String CASE_REFERENCE_TOO_LONG =
      "case_reference_number must be at most %d characters";
  public static final String CASE_REFERENCE_INVALID =
      "case_reference_number contains invalid characters; allowed: letters, digits, space, '/', '.', '-'";

  private static final Pattern ALLOWED_CASE_REFERENCE_PATTERN =
      Pattern.compile("^[a-zA-Z0-9/.\\s-]+$");

  /**
   * Validate the provided request.
   *
   * @param request the {@link ClaimSearchRequest} to validate. Must not be {@code null}.
   * @throws ClaimBadRequestException if the request is null, the office code is missing, or a
   *     supplied case reference number is shorter than {@link #MIN_CASE_REFERENCE_LENGTH} after
   *     trimming.
   */
  public void validate(ClaimSearchRequest request) {
    if (request == null) {
      throw new ClaimBadRequestException(MISSING_SEARCH_REQUEST);
    }
    validateOfficeCode(request.getOfficeCode());
    validateCaseReferenceNumber(request.getCaseReferenceNumber());
  }

  /**
   * Ensure the office code is present and contains non-whitespace characters.
   *
   * @param officeCode the office code to validate
   * @throws ClaimBadRequestException if {@code officeCode} is {@code null}, empty or only
   *     whitespace
   */
  public void validateOfficeCode(String officeCode) {
    if (!StringUtils.hasText(officeCode)) {
      throw new ClaimBadRequestException(MISSING_OFFICE_CODE);
    }
  }

  /**
   * Validate the case reference number according to these rules.
   *
   * <ol>
   *   <li>If {@code caseReferenceNumber} is {@code null} it is considered absent and no validation
   *       error is raised.
   *   <li>Trim leading/trailing whitespace. If the trimmed value is empty it is treated as absent
   *       (no validation error).
   *   <li>If the trimmed value is non-empty then it must be at least {@link
   *       #MIN_CASE_REFERENCE_LENGTH} and at most {@link #MAX_CASE_REFERENCE_LENGTH} characters
   *       long, otherwise a {@link ClaimBadRequestException} is thrown with a descriptive message.
   *   <li>The trimmed value must match the pattern <code>^[a-zA-Z0-9/\.\-\s]+$</code> (letters,
   *       digits, space, forward slash, dot and hyphen only) otherwise a {@link
   *       ClaimBadRequestException} is thrown.
   * </ol>
   *
   * @param caseReferenceNumber the raw case reference number supplied by the caller
   * @throws ClaimBadRequestException when a non-empty trimmed case reference number is outside the
   *     permitted length bounds or contains disallowed characters
   */
  public void validateCaseReferenceNumber(String caseReferenceNumber) {
    if (caseReferenceNumber == null) {
      return;
    }

    String trimmed = caseReferenceNumber.trim();

    if (trimmed.isEmpty()) {
      return;
    }

    if (trimmed.length() < MIN_CASE_REFERENCE_LENGTH) {
      throw new ClaimBadRequestException(
          String.format(CASE_REFERENCE_TOO_SHORT, MIN_CASE_REFERENCE_LENGTH));
    }

    if (trimmed.length() > MAX_CASE_REFERENCE_LENGTH) {
      throw new ClaimBadRequestException(
          String.format(CASE_REFERENCE_TOO_LONG, MAX_CASE_REFERENCE_LENGTH));
    }

    // Allowed characters: letters, digits, space, '/', '.', '-'
    if (!ALLOWED_CASE_REFERENCE_PATTERN.matcher(trimmed).matches()) {
      System.out.println(String.format("Case reference number '%s' is invalid", trimmed));
      throw new ClaimBadRequestException(CASE_REFERENCE_INVALID);
    }
  }
}
