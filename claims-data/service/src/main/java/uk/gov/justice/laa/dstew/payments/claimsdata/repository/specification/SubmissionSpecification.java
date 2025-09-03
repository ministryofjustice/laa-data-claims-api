package uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
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
   * @param submissionId an optional identifier to filter submissions by
   * @param submittedDateFrom an optional end date to filter submissions created on or after this
   *     date
   * @param submittedDateTo an optional end date to filter submissions created on or before this
   *     date
   * @return a JPA {@code Specification} of {@code Submission} containing the constructed filtering
   *     predicates
   */
  public static Specification<Submission> filterBy(
      final List<String> offices,
      final String submissionId,
      final LocalDate submittedDateFrom,
      final LocalDate submittedDateTo) {
    return (Root<Submission> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
      Predicate predicate = cb.and(root.get("officeAccountNumber").in(offices));

      if (StringUtils.hasText(submissionId)) {
        predicate = cb.and(predicate, cb.equal(root.get("id"), UUID.fromString(submissionId)));
      }

      if (submittedDateFrom != null) {
        predicate =
            cb.and(predicate, cb.greaterThanOrEqualTo(root.get("createdOn"), submittedDateFrom));
      }

      if (submittedDateTo != null) {
        // Criteria builder is converting dates into a format with time component at the start of
        // the day (e.g. '2025-09-01 00:00:00'),
        // which means a submission "created on" 2025-09-01 19:05:38 is not going to satisfy the
        // "lessThanOrEqualTo" predicate,
        // therefore being excluded by the filter.
        var endOfTheDay = submittedDateTo.plusDays(1).atStartOfDay().minusSeconds(1);

        predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("createdOn"), endOfTheDay));
      }
      return predicate;
    };
  }
}
