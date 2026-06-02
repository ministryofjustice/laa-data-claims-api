package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  private static final int DEFAULT_PAGE_NUMBER = 0;
  private static final int DEFAULT_PAGE_SIZE = 20;

  /** Implementation: validates against {@code fieldMap}, remaps aliases, appends id tie-breaker. */
  private static Pageable validateAndRemap(
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
    int pageNumber = pageable.isPaged() ? pageable.getPageNumber() : DEFAULT_PAGE_NUMBER;
    int pageSize = pageable.isPaged() ? pageable.getPageSize() : DEFAULT_PAGE_SIZE;
    return PageRequest.of(pageNumber, pageSize, sortWithTieBreaker);
  }

  /**
   * Validates and remaps the sort orders in {@code pageable} using the supplied {@link SortField}
   * enum values.
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
   * @param pageable the original pageable supplied by the controller; must not be {@code null}
   * @param fields the enum values of a {@link SortField} implementation; must not be {@code null}
   *     or empty
   * @param exceptionFactory produces the domain-specific bad-request exception for unrecognised
   *     fields; must not be {@code null}
   * @param ignoreCase when {@code true} applies case-insensitive ordering
   * @return a new validated, remapped {@link Pageable}
   * @throws NullPointerException if {@code pageable}, {@code fields} or {@code exceptionFactory} is
   *     {@code null}
   * @throws IllegalArgumentException if {@code fields} is empty
   */
  public static <T extends SortField> Pageable validateAndRemap(
      Pageable pageable,
      T[] fields,
      Function<String, ? extends RuntimeException> exceptionFactory,
      boolean ignoreCase) {
    Objects.requireNonNull(pageable, "pageable must not be null");
    Objects.requireNonNull(fields, "fields must not be null");
    Objects.requireNonNull(exceptionFactory, "exceptionFactory must not be null");
    if (fields.length == 0) {
      throw new IllegalArgumentException("fields must not be empty");
    }
    return validateAndRemap(pageable, buildFieldMap(fields), exceptionFactory, ignoreCase);
  }

  /** Builds an {@code apiFieldName → entityAlias} map from a {@link SortField} enum's values. */
  private static <T extends SortField> Map<String, String> buildFieldMap(T[] fields) {
    return Arrays.stream(fields)
        .collect(Collectors.toUnmodifiableMap(SortField::apiFieldName, SortField::entityAlias));
  }
}
