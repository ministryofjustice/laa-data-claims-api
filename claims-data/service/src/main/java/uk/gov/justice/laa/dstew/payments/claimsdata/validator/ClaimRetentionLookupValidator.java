package uk.gov.justice.laa.dstew.payments.claimsdata.validator;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimBadRequestException;

/** Validates the identifiers used by the claim retention lookup endpoint. */
@Component
public class ClaimRetentionLookupValidator {

  public static final String OFFICE_CODE_REQUIRED = "office_code is required.";
  public static final String OFFICE_CODE_INVALID =
      "office_code must contain exactly six alphanumeric characters.";
  public static final String UFN_REQUIRED = "ufn is required.";
  public static final String UFN_INVALID = "ufn must match DDMMYY/NNN.";

  private static final Pattern OFFICE_CODE_PATTERN = Pattern.compile("^[0-9A-Za-z]{6}$");
  private static final Pattern UFN_PATTERN = Pattern.compile("^[0-9]{6}/[0-9]{3}$");

  public void validate(String officeCode, String ufn) {
    validateOfficeCode(officeCode);
    validateUfn(ufn);
  }

  /**
   * Validates that the office code is present and contains exactly six alphanumeric characters.
   *
   * @param officeCode the office code to validate
   * @throws ClaimBadRequestException if the office code is missing or invalid
   */
  public void validateOfficeCode(String officeCode) {
    if (!StringUtils.hasText(officeCode)) {
      throw new ClaimBadRequestException(OFFICE_CODE_REQUIRED);
    }
    if (!OFFICE_CODE_PATTERN.matcher(officeCode).matches()) {
      throw new ClaimBadRequestException(OFFICE_CODE_INVALID);
    }
  }

  /**
   * Validates that the UFN is present and matches the {@code DDMMYY/NNN} format.
   *
   * @param ufn the UFN to validate
   * @throws ClaimBadRequestException if the UFN is missing or invalid
   */
  public void validateUfn(String ufn) {
    if (!StringUtils.hasText(ufn)) {
      throw new ClaimBadRequestException(UFN_REQUIRED);
    }
    if (!UFN_PATTERN.matcher(ufn).matches()) {
      throw new ClaimBadRequestException(UFN_INVALID);
    }
  }
}
