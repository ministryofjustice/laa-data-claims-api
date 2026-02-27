package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;

/** Enum class to map between sorting naming exposed by the API and actual DB structure. */
@Getter
public enum ClaimSortField {
  OFFICE_CODE("office_code", "submission.officeAccountNumber"),
  AREA_OF_LAW("area_of_law", "submission.areaOfLaw"),

  LINE_NUMBER("line_number", "lineNumber"),

  CLIENT_SURNAME("client_surname", "client.clientSurname"),
  CLIENT_FORENAME("client_forename", "client.clientForename"),

  UNIQUE_FILE_NUMBER("unique_file_number", "uniqueFileNumber"),
  UNIQUE_CLIENT_NUMBER("unique_client_number", "client.uniqueClientNumber"),
  CLAIM_STATUS("status", "status"),

  CASE_REFERENCE_NUMBER("case_reference_number", "caseReferenceNumber"),

  DATE_SUBMITTED("date_submitted", "submission.createdOn"),

  FEE_CODE("fee_code", "feeCode"),
  CALCULATED_VAT_AMOUNT("calculated_vat_amount", "calculatedFeeDetail.calculatedVatAmount"),
  ESCAPE_CASE_FLAG("escape_case_flag", "calculatedFeeDetail.escapeCaseFlag"),

  TOTAL_WARNINGS("total_warnings", "totalWarnings");

  private final String apiName;
  private final String entityPath;

  ClaimSortField(String apiName, String entityPath) {
    this.apiName = apiName;
    this.entityPath = entityPath;
  }

  public static Optional<ClaimSortField> fromApiName(String apiName) {
    return Arrays.stream(values()).filter(f -> f.apiName.equals(apiName)).findFirst();
  }
}
