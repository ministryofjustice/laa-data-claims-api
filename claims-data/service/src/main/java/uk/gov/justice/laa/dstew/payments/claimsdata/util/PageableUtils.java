package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Utility methods for validating and remapping {@link Pageable} sort orders.
 *
 * <p>The main entry point is {@link #validateAndRemap}, which:
 *
 * <ol>
 *   <li>Rejects any sort field not present in the supplied {@code fieldMap};
 *   <li>Translates API-facing field names to their internal aliases / entity paths;
 *   <li>Optionally applies case-insensitive ordering;
 *   <li>Appends a deterministic secondary sort by {@code id} (same direction as the primary sort)
 *       so rows never drift between pages.
 * </ol>
 */
@UtilityClass
public class PageableUtils {

  /**
   * Validates and remaps the sort orders in {@code pageable}.
   *
   * @param pageable the original pageable supplied by the controller
   * @param fieldMap a map from API field name to the internal alias / entity path used in the
   *     query; only keys present in this map are accepted as valid sort fields
   * @param exceptionFactory a function that produces the domain-specific bad-request exception for
   *     an unrecognised field name, e.g. {@code MyBadRequestException::new}
   * @param ignoreCase when {@code true} each remapped {@link Sort.Order} is flagged with {@link
   *     Sort.Order#ignoreCase()}, enabling case-insensitive database ordering
   * @return a new {@link Pageable} with validated, remapped, and (optionally) case-insensitive sort
   *     orders, plus a deterministic secondary sort by {@code id}
   * @throws RuntimeException (subtype determined by {@code exceptionFactory}) if an unrecognised
   *     sort field is requested
   */
  public static Pageable validateAndRemap(
      Pageable pageable,
      Map<String, String> fieldMap,
      Function<String, ? extends RuntimeException> exceptionFactory,
      boolean ignoreCase) {

    Set<String> allowedFields = fieldMap.keySet();

    pageable
        .getSort()
        .forEach(
            order -> {
              if (!allowedFields.contains(order.getProperty())) {
                throw exceptionFactory.apply(
                    "Invalid sort field: '"
                        + order.getProperty()
                        + "'. Allowed fields: "
                        + allowedFields);
              }
            });

    Sort.Direction tieBreakerDirection =
        pageable.getSort().isSorted()
            ? pageable.getSort().iterator().next().getDirection()
            : Sort.Direction.ASC;

    List<Sort.Order> remappedOrders =
        pageable.getSort().stream()
            .map(
                order -> {
                  String alias = fieldMap.getOrDefault(order.getProperty(), order.getProperty());
                  Sort.Order remapped = new Sort.Order(order.getDirection(), alias);
                  return ignoreCase ? remapped.ignoreCase() : remapped;
                })
            .toList();

    Sort sortWithTieBreaker = Sort.by(remappedOrders).and(Sort.by(tieBreakerDirection, "id"));
    int pageNumber = pageable.isPaged() ? pageable.getPageNumber() : 0;
    int pageSize = pageable.isPaged() ? pageable.getPageSize() : 20;
    return PageRequest.of(pageNumber, pageSize, sortWithTieBreaker);
  }

  /**
   * Convenience overload that accepts an array of {@link SortField} enum values and builds the
   * {@code fieldMap} internally, so callers do not need to construct it themselves.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * PageableUtils.validateAndRemap(
   *     pageable,
   *     ValidationMessageSortField.values(),
   *     ValidationMessageBadRequestException::new,
   *     true);
   * }</pre>
   *
   * @param pageable the original pageable supplied by the controller
   * @param fields the enum values of a {@link SortField} implementation
   * @param exceptionFactory produces the domain-specific bad-request exception for unrecognised
   *     fields
   * @param ignoreCase when {@code true} applies case-insensitive ordering
   * @return a new validated, remapped {@link Pageable}
   */
  public static <T extends SortField> Pageable validateAndRemap(
      Pageable pageable,
      T[] fields,
      Function<String, ? extends RuntimeException> exceptionFactory,
      boolean ignoreCase) {
    return validateAndRemap(pageable, buildFieldMap(fields), exceptionFactory, ignoreCase);
  }

  /**
   * Builds an unmodifiable {@code apiName → entityAlias} map from an array of {@link SortField}
   * enum values.
   *
   * @param fields the enum values to map
   * @return an unmodifiable map from API name to entity alias
   */
  public static <T extends SortField> Map<String, String> buildFieldMap(T[] fields) {
    return Arrays.stream(fields)
        .collect(Collectors.toUnmodifiableMap(SortField::apiFieldName, SortField::entityAlias));
  }
}
