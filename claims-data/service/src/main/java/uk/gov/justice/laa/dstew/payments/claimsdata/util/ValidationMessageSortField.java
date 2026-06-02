package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import java.util.Arrays;
import java.util.Optional;

/**
 * Maps the API-facing sort field names for the {@code getValidationMessages} endpoint to the
 * corresponding JPQL projection alias used in {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository
 * #findWithClaimDetailsByFilters}.
 *
 * <p><strong>Null / blank handling:</strong> when a row's sort value is {@code NULL} or an empty
 * string, it is ordered <em>last</em> for ascending sorts and <em>first</em> for descending sorts
 * (PostgreSQL default {@code NULLS LAST} / {@code NULLS FIRST} behaviour is preserved). This
 * ensures a stable, predictable position for incomplete records regardless of sort direction.
 */
public enum ValidationMessageSortField implements SortField {
  DISPLAY_MESSAGE("display_message", "displayMessage"),

  CLIENT_FORENAME("client_forename", "clientForename"),
  CLIENT_SURNAME("client_surname", "clientSurname"),
  UNIQUE_CLIENT_NUMBER("unique_client_number", "uniqueClientNumber"),

  CLIENT_2_FORENAME("client_2_forename", "client2Forename"),
  CLIENT_2_SURNAME("client_2_surname", "client2Surname"),
  CLIENT_2_UCN("client_2_ucn", "client2Ucn"),

  UNIQUE_FILE_NUMBER("unique_file_number", "uniqueFileNumber");

  /** The field name accepted in the {@code sort} query parameter (e.g. {@code client_surname}). */
  private final String apiName;

  /**
   * The alias as it appears in the JPQL projection SELECT clause (e.g. {@code clientSurname}).
   * Spring Data JPA uses this alias when building the ORDER BY clause.
   */
  private final String projectionAlias;

  ValidationMessageSortField(String apiName, String projectionAlias) {
    this.apiName = apiName;
    this.projectionAlias = projectionAlias;
  }

  @Override
  public String apiFieldName() {
    return apiName;
  }

  @Override
  public String entityAlias() {
    return projectionAlias;
  }

  /**
   * Looks up a sort field by its API name.
   *
   * @param apiName the value supplied in the {@code sort} query parameter
   * @return an {@link Optional} containing the matching field, or {@link Optional#empty()} if no
   *     match is found
   */
  public static Optional<ValidationMessageSortField> fromApiName(String apiName) {
    return Arrays.stream(values()).filter(f -> f.apiName.equals(apiName)).findFirst();
  }
}
