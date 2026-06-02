package uk.gov.justice.laa.dstew.payments.claimsdata.util;

/**
 * Maps the API-facing sort field names for the {@code getSubmissions} endpoint to the corresponding
 * entity field or {@code @Formula} sort-key used in queries.
 *
 * <p>{@code submissionPeriod} is mapped to {@code submissionPeriodSortKey} (a {@code @Formula}
 * field) to ensure correct chronological ordering. Case-insensitive ordering for string fields is
 * handled via {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.util.PageableUtils#validateAndRemap} rather than a
 * dedicated sort-key field.
 *
 * <p><strong>Null handling:</strong> PostgreSQL applies {@code NULLS LAST} for {@code ASC} and
 * {@code NULLS FIRST} for {@code DESC} by default.
 */
public enum SubmissionSortField implements SortField {
  CREATED_ON("createdOn", "createdOn"),
  OFFICE_ACCOUNT_NUMBER("officeAccountNumber", "officeAccountNumber"),
  AREA_OF_LAW("areaOfLaw", "areaOfLaw"),
  SUBMISSION_PERIOD("submissionPeriod", "submissionPeriodSortKey"),
  STATUS("status", "status");

  private final String apiName;
  private final String entityAlias;

  SubmissionSortField(String apiName, String entityAlias) {
    this.apiName = apiName;
    this.entityAlias = entityAlias;
  }

  @Override
  public String apiFieldName() {
    return apiName;
  }

  @Override
  public String entityAlias() {
    return entityAlias;
  }
}
