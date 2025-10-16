package uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_STATUSES;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

class SubmissionSpecificationTest {

  @SuppressWarnings("unchecked")
  private final Root<Submission> root = Mockito.mock(Root.class);

  private final CriteriaQuery<?> query = Mockito.mock(CriteriaQuery.class);
  private final CriteriaBuilder cb = Mockito.mock(CriteriaBuilder.class);

  @Test
  @DisplayName("should build specification with only mandatory offices")
  void shouldBuildSpecificationWithOnlyMandatoryOffices() {
    Specification<Submission> spec =
        SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("OFFICE1", "OFFICE2"));

    Predicate officePredicate = Mockito.mock(Predicate.class);
    Mockito.when(root.get("officeAccountNumber"))
        .thenReturn(Mockito.mock(jakarta.persistence.criteria.Path.class));
    Mockito.when(cb.and(Mockito.any())).thenReturn(officePredicate);

    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
  }

  @DisplayName("should return equals predicate when submission id present")
  @Test
  void shouldBuildSpecificationWithSubmissionId() {
    UUID submissionId = Uuid7.timeBasedUuid();
    Specification<Submission> spec =
        SubmissionSpecification.submissionIdEqualTo(submissionId.toString());

    Predicate withSubmissionId = Mockito.mock(Predicate.class);

    Mockito.when(root.get("id")).thenReturn(Mockito.mock(jakarta.persistence.criteria.Path.class));
    Mockito.when(cb.equal(Mockito.any(), Mockito.eq(submissionId))).thenReturn(withSubmissionId);

    var actualResults = spec.toPredicate(root, query, cb);

    assertThat(actualResults).isNotNull();
  }

  @DisplayName("should return predicate when submission id not present")
  @Test
  void shouldBuildSpecificationWithoutSubmissionIdWhenNull() {
    Specification<Submission> spec = SubmissionSpecification.submissionIdEqualTo(null);

    Mockito.when(cb.conjunction()).thenReturn(Mockito.mock(Predicate.class));
    var actualResults = spec.toPredicate(root, query, cb);
    verify(cb, times(0)).equal(Mockito.any(), Mockito.any());

    assertThat(actualResults).isNotNull();
  }

  @DisplayName("should greater than or equals predicate for Date on or after Created on date")
  @Test
  void shouldBuildSpecificationWithCreatedOnDate() {

    LocalDate date = LocalDate.of(2025, 9, 1);
    Specification<Submission> spec = SubmissionSpecification.createdOnOrAfter(date);

    Predicate mockedDatePredicate = Mockito.mock(Predicate.class);

    Mockito.when(root.get("createdOn"))
        .thenReturn(Mockito.mock(jakarta.persistence.criteria.Path.class));
    Mockito.when(cb.greaterThanOrEqualTo(Mockito.any(), Mockito.eq(date)))
        .thenReturn(mockedDatePredicate);

    var actualResults = spec.toPredicate(root, query, cb);

    assertThat(actualResults).isNotNull();
  }

  @DisplayName("should return predicate when date on or after is null")
  @Test
  void shouldBuildSpecificationWithCreatedOnDateWhenNull() {
    Specification<Submission> spec = SubmissionSpecification.createdOnOrAfter(null);

    Mockito.when(cb.conjunction()).thenReturn(Mockito.mock(Predicate.class));
    var actualResults = spec.toPredicate(root, query, cb);
    verify(cb, times(0)).greaterThanOrEqualTo(Mockito.any(), Mockito.any(LocalDateTime.class));

    assertThat(actualResults).isNotNull();
  }

  @DisplayName(
      "should return Specification to filter submissions created on or before a provided date")
  @Test
  void shouldBuildSpecificationWithCreatedOnDateBefore() {
    LocalDate date = LocalDate.of(2025, 9, 1);
    Specification<Submission> spec = SubmissionSpecification.createdOnOrBefore(date);

    Predicate mockedDatePredicate = Mockito.mock(Predicate.class);

    Mockito.when(root.get("createdOn"))
        .thenReturn(Mockito.mock(jakarta.persistence.criteria.Path.class));
    Mockito.when(
            cb.lessThanOrEqualTo(
                Mockito.any(), Mockito.eq(date.plusDays(1).atStartOfDay().minusSeconds(1))))
        .thenReturn(mockedDatePredicate);

    var actualResults = spec.toPredicate(root, query, cb);

    assertThat(actualResults).isNotNull();
  }

  @DisplayName("should return conjunction predicate when provided date is null")
  @Test
  void shouldBuildSpecificationWithCreatedOnDateBeforeWhenNull() {
    Specification<Submission> spec = SubmissionSpecification.createdOnOrBefore(null);

    Mockito.when(cb.conjunction()).thenReturn(Mockito.mock(Predicate.class));
    var actualResults = spec.toPredicate(root, query, cb);
    verify(cb, times(0)).lessThanOrEqualTo(Mockito.any(), Mockito.any(LocalDateTime.class));

    assertThat(actualResults).isNotNull();
  }

  @DisplayName("should return equals predicate when area of code is present")
  @Test
  void shouldBuildSpecificationWithAreaOfLaw() {
    Specification<Submission> spec = SubmissionSpecification.areaOfLawEqual("CIVIL");

    Predicate withAreaOfCode = Mockito.mock(Predicate.class);

    Mockito.when(root.get("areaOfLaw"))
        .thenReturn(Mockito.mock(jakarta.persistence.criteria.Path.class));
    Mockito.when(cb.equal(Mockito.any(), Mockito.eq("CIVIL"))).thenReturn(withAreaOfCode);

    var actualResults = spec.toPredicate(root, query, cb);

    assertThat(actualResults).isNotNull();
  }

  @DisplayName("should return conjunction predicate when area of code is null")
  @Test
  void shouldBuildSpecificationWithoutAreaOfLawWhenNull() {
    Specification<Submission> spec = SubmissionSpecification.areaOfLawEqual(null);

    Mockito.when(cb.conjunction()).thenReturn(Mockito.mock(Predicate.class));
    var actualResults = spec.toPredicate(root, query, cb);
    verify(cb, times(0)).equal(Mockito.any(), Mockito.any());

    assertThat(actualResults).isNotNull();
  }

  @DisplayName("should return equals predicate when submissionPeriod is present")
  @Test
  void shouldBuildSpecificationWithSubmissionPeriod() {
    Specification<Submission> spec = SubmissionSpecification.submissionPeriodEqual("2025-09-01");

    Predicate withDate = Mockito.mock(Predicate.class);

    Mockito.when(root.get("submissionPeriod"))
        .thenReturn(Mockito.mock(jakarta.persistence.criteria.Path.class));
    Mockito.when(cb.equal(Mockito.any(), Mockito.eq("2025-09-01"))).thenReturn(withDate);
    var actualResults = spec.toPredicate(root, query, cb);

    assertThat(actualResults).isNotNull();
  }

  @DisplayName("should return predicate when submissionPeriod is not present")
  @Test
  void shouldBuildSpecificationWithoutSubmissionPeriodWhenNull() {

    Specification<Submission> spec = SubmissionSpecification.submissionPeriodEqual(null);
    Mockito.when(cb.conjunction()).thenReturn(Mockito.mock(Predicate.class));
    var actualResults = spec.toPredicate(root, query, cb);
    verify(cb, times(0)).equal(Mockito.any(), Mockito.any());

    assertThat(actualResults).isNotNull();
  }

  @DisplayName("should return no predicate when submission statuses is null")
  @Test
  void shouldBuildSpecificationWithSubmissionStatusesWhenNull() {
    Specification<Submission> spec = SubmissionSpecification.submissionStatusIn(null);

    Mockito.when(cb.conjunction()).thenReturn(Mockito.mock(Predicate.class));
    var actualResults = spec.toPredicate(root, query, cb);
    verify(cb, never()).in(Mockito.any());

    assertThat(actualResults).isNotNull();
  }

  @DisplayName("should return and predicate when submission statuses is not null")
  @Test
  void shouldBuildSpecificationWithSubmissionStatusesNotNull() {
    Specification<Submission> spec =
        SubmissionSpecification.submissionStatusIn(SUBMISSION_STATUSES);

    Predicate submissionStatusesPredicate = Mockito.mock(Predicate.class);
    Mockito.when(root.get("status"))
        .thenReturn(Mockito.mock(jakarta.persistence.criteria.Path.class));
    Mockito.when(cb.and(Mockito.any())).thenReturn(submissionStatusesPredicate);

    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
  }
}
