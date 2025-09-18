package uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.uuid.Generators;
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

class SubmissionSpecificationTest {

  @SuppressWarnings("unchecked")
  private final Root<Submission> root = Mockito.mock(Root.class);

  private final CriteriaQuery<?> query = Mockito.mock(CriteriaQuery.class);
  private final CriteriaBuilder cb = Mockito.mock(CriteriaBuilder.class);

  @Test
  @DisplayName("should build specification with only mandatory offices")
  void shouldBuildSpecificationWithOnlyMandatoryOffices() {
    Specification<Submission> spec =
        SubmissionSpecification.filterBy(List.of("OFFICE1", "OFFICE2"), null, null, null);

    Predicate officePredicate = Mockito.mock(Predicate.class);
    Mockito.when(root.get("officeAccountNumber"))
        .thenReturn(Mockito.mock(jakarta.persistence.criteria.Path.class));
    Mockito.when(cb.and(Mockito.any())).thenReturn(officePredicate);

    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("should build specification with submissionId")
  void shouldBuildSpecificationWithSubmissionId() {
    UUID submissionId = Generators.timeBasedEpochGenerator().generate();

    Specification<Submission> spec =
        SubmissionSpecification.filterBy(List.of("OFFICE1"), submissionId.toString(), null, null);

    Predicate base = Mockito.mock(Predicate.class);
    Predicate withId = Mockito.mock(Predicate.class);

    Mockito.when(root.get("officeAccountNumber"))
        .thenReturn(Mockito.mock(jakarta.persistence.criteria.Path.class));
    Mockito.when(root.get("id")).thenReturn(Mockito.mock(jakarta.persistence.criteria.Path.class));
    Mockito.when(cb.and(Mockito.any())).thenReturn(base);
    Mockito.when(cb.equal(Mockito.any(), Mockito.eq(submissionId))).thenReturn(withId);
    Mockito.when(cb.and(base, withId)).thenReturn(withId);

    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("should build specification with submittedDateFrom")
  void shouldBuildSpecificationWithSubmittedDateFrom() {
    LocalDate submittedDateFrom = LocalDate.of(2025, 1, 1);

    Specification<Submission> spec =
        SubmissionSpecification.filterBy(List.of("OFFICE1"), null, submittedDateFrom, null);

    Predicate base = Mockito.mock(Predicate.class);
    Predicate withDate = Mockito.mock(Predicate.class);

    Mockito.when(root.get("officeAccountNumber"))
        .thenReturn(Mockito.mock(jakarta.persistence.criteria.Path.class));
    Mockito.when(root.get("createdOn"))
        .thenReturn(Mockito.mock(jakarta.persistence.criteria.Path.class));
    Mockito.when(cb.and(Mockito.any())).thenReturn(base);
    Mockito.when(cb.greaterThanOrEqualTo(Mockito.any(), Mockito.eq(submittedDateFrom)))
        .thenReturn(withDate);
    Mockito.when(cb.and(base, withDate)).thenReturn(withDate);

    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("should build specification with submittedDateTo including end of day adjustment")
  void shouldBuildSpecificationWithSubmittedDateTo() {
    LocalDate submittedDateTo = LocalDate.of(2025, 9, 1);

    Specification<Submission> spec =
        SubmissionSpecification.filterBy(List.of("OFFICE1"), null, null, submittedDateTo);

    Predicate base = Mockito.mock(Predicate.class);
    Predicate withDate = Mockito.mock(Predicate.class);

    Mockito.when(root.get("officeAccountNumber"))
        .thenReturn(Mockito.mock(jakarta.persistence.criteria.Path.class));
    Mockito.when(root.get("createdOn"))
        .thenReturn(Mockito.mock(jakarta.persistence.criteria.Path.class));
    Mockito.when(cb.and(Mockito.any())).thenReturn(base);
    Mockito.when(cb.lessThanOrEqualTo(Mockito.any(), Mockito.any(LocalDateTime.class)))
        .thenReturn(withDate);
    Mockito.when(cb.and(base, withDate)).thenReturn(withDate);

    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
  }
}
