package uk.gov.justice.laa.dstew.payments.claimsdata.util;

/**
 * Marker interface for sort-field enums that map an API-facing field name to an internal entity
 * alias or path used in queries.
 *
 * <p>Implementing this interface allows {@link PageableUtils#buildFieldMap(SortField[])} to build
 * the {@code fieldMap} required by {@link PageableUtils#validateAndRemap} without the caller having
 * to construct the map manually.
 */
public interface SortField {

  /** The field name accepted in the {@code sort} query parameter (e.g. {@code client_surname}). */
  String apiFieldName();

  /**
   * The internal alias or entity path used in the query (e.g. {@code clientSurname} or {@code
   * client.clientSurname}).
   */
  String entityAlias();
}
