package uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;

/**
 * This class provide basic filtering logic to query {@link Submission} entities using JPA {@link
 * Specification}.
 *
 * <p>The resulting specification can be used with Spring Data JPA repositories to fetch filtered
 * submission records from the database.
 */
public final class SubmissionSpecification {

  /**
   * Constructs a JPA {@link Specification} for filtering {@link Submission} records based on
   * various parameters. The resulting specification can be used to dynamically generate predicates
   * for querying submissions.
   *
   * @param offices a mandatory list of office codes to filter submissions by
   * @return a JPA {@code Specification} of {@code Submission} containing the constructed filtering
   *     predicates
   */
  public static Specification<Submission> filterByOfficeAccountNumberIn(
      final List<String> offices) {
    return (Root<Submission> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
        cb.and(root.get("officeAccountNumber").in(offices));
  }

  /**
   * Constructs a JPA {@link Specification} for submission id equals to.
   *
   * @param submissionId an optional String
   * @return a JPA {@code Specification}
   */
  public static Specification<Submission> submissionIdEqualTo(final String submissionId) {
    return (root, query, cb) ->
        Optional.ofNullable(submissionId).isPresent()
            ? cb.equal(root.get("id"), UUID.fromString(submissionId))
            : cb.conjunction();
  }

  /**
   * Constructs a JPA {@link Specification} for date on or after createdOn date.
   *
   * @param date an optional Date
   * @return a JPA {@code Specification}
   */
  public static Specification<Submission> createdOnOrAfter(final LocalDate date) {
    return (root, query, cb) ->
        Optional.ofNullable(date).isPresent()
            ? cb.greaterThanOrEqualTo(root.get("createdOn"), date)
            : cb.conjunction();
  }

  /**
   * Constructs a JPA {@link Specification} for date on or before createdOn date.
   *
   * @param date an optional Date
   * @return a JPA {@code Specification}
   */
  public static Specification<Submission> createdOnOrBefore(final LocalDate date) {
    return (root, query, cb) ->
        Optional.ofNullable(date).isPresent()
            ? cb.lessThanOrEqualTo(
                root.get("createdOn"), date.plusDays(1).atStartOfDay().minusSeconds(1))
            : cb.conjunction();
  }

  /**
   * Constructs a JPA {@link Specification} for filtering {@link Submission} area of law equal to.
   *
   * @param areaOfLaw an optional identifier to filter by area of law
   * @return a JPA {@code Specification}
   */
  public static Specification<Submission> areaOfLawEqual(final String areaOfLaw) {
    return (root, query, cb) ->
        Optional.ofNullable(areaOfLaw).isPresent()
            ? cb.equal(root.get("areaOfLaw"), areaOfLaw)
            : cb.conjunction();
  }

  /**
   * Constructs a JPA {@link Specification} for filtering {@link Submission} submission period equal
   * to.
   *
   * @param submissionPeriod an optional identifier to filter by the submission period
   * @return a JPA {@code Specification}
   */
  public static Specification<Submission> submissionPeriodEqual(final String submissionPeriod) {
    return (root, query, cb) ->
        Optional.ofNullable(submissionPeriod).isPresent()
            ? cb.equal(root.get("submissionPeriod"), submissionPeriod)
            : cb.conjunction();
  }
}
