package uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;

class ClaimSpecificationTest {

  @Mock private Root<Claim> root;

  @Mock private CriteriaQuery<Claim> query;

  @Mock private CriteriaBuilder cb;

  @Mock private Join<?, ?> submissionJoin;

  @Mock private Join<?, ?> clientJoin;

  @Mock private Join<?, ?> claimCaseJoin;

  @Mock private Join<?, ?> calculatedFeeDetailJoin;

  @Mock private Predicate predicate1;

  @Mock private Predicate predicate2;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  // -------------------------------------------------------------------------
  // filterBy(String, ...)
  // -------------------------------------------------------------------------

  @Test
  void filterBy_withMinimalMandatoryParams_buildsPredicate() {
    // given
    String officeCode = "OFF-123";
    String submissionId = null;
    List<SubmissionStatus> submissionStatuses = null;
    String feeCode = null;
    String uniqueFileNumber = null;
    String uniqueClientNumber = null;
    String uniqueCaseId = null;
    List<ClaimStatus> claimStatuses = null;
    String submissionPeriod = null;
    String caseReferenceNumber = null;

    // mock submission join
    when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);

    // mock office code predicate
    when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
        .thenReturn(predicate1);
    when(cb.and(predicate1)).thenReturn(predicate1);

    when(cb.and(any(Predicate[].class))).thenReturn(predicate1);

    Specification<Claim> spec =
        ClaimSpecification.filterBy(
            officeCode,
            submissionId,
            submissionStatuses,
            feeCode,
            uniqueFileNumber,
            uniqueClientNumber,
            uniqueCaseId,
            claimStatuses,
            submissionPeriod,
            caseReferenceNumber);

    // when
    Predicate result = spec.toPredicate(root, query, cb);

    // then
    assertThat(result).isNotNull();
    verify(root).join(ClaimSpecification.SUBMISSION_ENTITY);
    verify(cb).equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode);
    //        verify(cb).and(any(Predicate[].class));
  }

  @Test
  void filterBy_withUniqueClientAndCase_usesSubqueries() {
    // given
    String officeCode = "OFF-123";
    String uniqueClientNumber = "CL-999";
    String uniqueCaseId = "CASE-001";

    when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);

    Predicate officePredicate = mock(Predicate.class);
    when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
        .thenReturn(officePredicate);
    when(cb.and(any(Predicate.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // Client subquery
    Subquery<Client> clientSubquery = mock(Subquery.class);
    Root<Client> clientRoot = mock(Root.class);

    when(query.subquery(Client.class)).thenReturn(clientSubquery);
    when(clientSubquery.from(Client.class)).thenReturn(clientRoot);

    Predicate clientPredicate1 = mock(Predicate.class);
    Predicate clientPredicate2 = mock(Predicate.class);

    when(cb.equal(clientRoot.get(ClaimSpecification.CLAIM_ENTITY), root))
        .thenReturn(clientPredicate1);
    when(cb.equal(clientRoot.get(ClaimSpecification.UNIQUE_CLIENT_NUMBER), uniqueClientNumber))
        .thenReturn(clientPredicate2);

    when(clientSubquery.select(clientRoot.get(ClaimSpecification.ID))).thenReturn(clientSubquery);
    when(clientSubquery.where(clientPredicate1, clientPredicate2)).thenReturn(clientSubquery);

    Predicate clientExistsPredicate = mock(Predicate.class);
    when(cb.exists(clientSubquery)).thenReturn(clientExistsPredicate);

    // ClaimCase subquery
    Subquery<ClaimCase> claimCaseSubquery = mock(Subquery.class);
    Root<ClaimCase> claimCaseRoot = mock(Root.class);

    when(query.subquery(ClaimCase.class)).thenReturn(claimCaseSubquery);
    when(claimCaseSubquery.from(ClaimCase.class)).thenReturn(claimCaseRoot);

    Predicate claimCasePredicate1 = mock(Predicate.class);
    Predicate claimCasePredicate2 = mock(Predicate.class);

    when(cb.equal(claimCaseRoot.get(ClaimSpecification.CLAIM_ENTITY), root))
        .thenReturn(claimCasePredicate1);
    when(cb.equal(claimCaseRoot.get(ClaimSpecification.UNIQUE_CASE_ID), uniqueCaseId))
        .thenReturn(claimCasePredicate2);

    when(claimCaseSubquery.select(claimCaseRoot.get(ClaimSpecification.ID)))
        .thenReturn(claimCaseSubquery);
    when(claimCaseSubquery.where(claimCasePredicate1, claimCasePredicate2))
        .thenReturn(claimCaseSubquery);

    Predicate claimCaseExistsPredicate = mock(Predicate.class);
    when(cb.exists(claimCaseSubquery)).thenReturn(claimCaseExistsPredicate);

    when(cb.and(any(Predicate[].class))).thenReturn(predicate1);

    Specification<Claim> spec =
        ClaimSpecification.filterBy(
            officeCode, null, null, null, null, uniqueClientNumber, uniqueCaseId, null, null, null);

    // when
    Predicate result = spec.toPredicate(root, query, cb);

    // then
    assertThat(result).isNotNull();
    verify(query).subquery(Client.class);
    verify(query).subquery(ClaimCase.class);
    verify(cb).exists(clientSubquery);
    verify(cb).exists(claimCaseSubquery);
  }

  // -------------------------------------------------------------------------
  // orderByTotalWarningMessages(Pageable)
  // -------------------------------------------------------------------------

  @Test
  void orderByTotalWarningMessages_withNullPageable_returnsConjunction() {
    // given
    Pageable pageable = null;
    when(cb.conjunction()).thenReturn(predicate1);

    Specification<Claim> spec = ClaimSpecification.orderByTotalWarningMessages(pageable);

    // when
    Predicate result = spec.toPredicate(root, query, cb);

    // then
    assertThat(result).isEqualTo(predicate1);
    verify(cb).conjunction();
    verifyNoMoreInteractions(query);
  }

  @Test
  void orderByTotalWarningMessages_withNoSort_returnsConjunction() {
    // given
    Pageable pageable = PageRequest.of(0, 10, Sort.unsorted());
    when(cb.conjunction()).thenReturn(predicate1);

    Specification<Claim> spec = ClaimSpecification.orderByTotalWarningMessages(pageable);

    // when
    Predicate result = spec.toPredicate(root, query, cb);

    // then
    assertThat(result).isEqualTo(predicate1);
    verify(cb).conjunction();
    verifyNoMoreInteractions(query);
  }

  @Test
  void orderByTotalWarningMessages_withNonMatchingSortProperty_doesNotAlterQuery() {
    // given
    Pageable pageable = PageRequest.of(0, 10, Sort.by("someOtherField"));
    when(cb.conjunction()).thenReturn(predicate1);

    Specification<Claim> spec = ClaimSpecification.orderByTotalWarningMessages(pageable);

    // when
    Predicate result = spec.toPredicate(root, query, cb);

    // then
    assertThat(result).isEqualTo(predicate1);
    verify(cb).conjunction();
    verifyNoMoreInteractions(query);
  }

  @Test
  void orderByTotalWarningMessages_withTotalWarningsSort_addsSubqueryOrderBy() {
    // given
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.asc("totalWarnings")));

    when(cb.conjunction()).thenReturn(predicate1);

    Subquery<Long> warningSubquery = mock(Subquery.class);
    Root<ValidationMessageLog> vmlRoot = mock(Root.class);

    when(query.subquery(Long.class)).thenReturn(warningSubquery);
    when(warningSubquery.from(ValidationMessageLog.class)).thenReturn(vmlRoot);

    Expression<Long> countExpression = mock(Expression.class);
    when(cb.count(vmlRoot)).thenReturn(countExpression);

    Predicate claimIdPredicate = mock(Predicate.class);
    Predicate typePredicate = mock(Predicate.class);

    when(cb.equal(vmlRoot.get("claimId"), root.get(ClaimSpecification.ID)))
        .thenReturn(claimIdPredicate);
    when(cb.equal(vmlRoot.get("type"), ValidationMessageType.WARNING)).thenReturn(typePredicate);

    when(warningSubquery.select(countExpression)).thenReturn(warningSubquery);
    when(warningSubquery.where(claimIdPredicate, typePredicate)).thenReturn(warningSubquery);

    // when
    Specification<Claim> spec = ClaimSpecification.orderByTotalWarningMessages(pageable);
    Predicate result = spec.toPredicate(root, query, cb);

    // then
    assertThat(result).isEqualTo(predicate1); // conjunction
    verify(query).subquery(Long.class);
    //        verify(query).orderBy(any());
  }
}
