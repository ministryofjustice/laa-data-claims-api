package uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.sql.Date;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.ClaimSearchRequest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;

class ClaimSpecificationTest {

  @Mock private Root<Claim> root;

  @Mock private CriteriaQuery<Claim> query;

  @Mock private CriteriaBuilder cb;

  @Mock private Join<Claim, Submission> submissionJoin;

  @Mock private Predicate predicate1;

  @Mock private Predicate predicate2;

  private AutoCloseable mocks;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  // -------------------------------------------------------------------------
  // filterBy(String, ...)
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("filterBy(String,...)")
  class FilterByTests {

    @Test
    @DisplayName("with minimal mandatory params builds predicate")
    void filterByWithMinimalMandatoryParamsBuildsPredicate() {
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
    @DisplayName("with unique client and case uses subqueries")
    void filterByWithUniqueClientAndCaseUsesSubqueries() {
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
              officeCode,
              null,
              null,
              null,
              null,
              uniqueClientNumber,
              uniqueCaseId,
              null,
              null,
              null);

      // when
      Predicate result = spec.toPredicate(root, query, cb);

      // then
      assertThat(result).isNotNull();
      verify(query).subquery(Client.class);
      verify(query).subquery(ClaimCase.class);
      verify(cb).exists(clientSubquery);
      verify(cb).exists(claimCaseSubquery);
    }

    @Test
    @DisplayName("with case reference number uses case-insensitive like")
    void filterByWithCaseReferenceNumberUsesCaseInsensitiveLike() {
      // given
      String officeCode = "OFF-123";
      String caseReferenceNumber = "ABC";

      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);

      // mock office predicate
      Predicate officePredicate = mock(Predicate.class);
      when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
          .thenReturn(officePredicate);
      when(cb.and(any(Predicate[].class))).thenReturn(predicate1);

      // mock case reference handling: root.get(...), cb.lower(...), cb.like(...)
      Path<String> casePath = mock(Path.class);
      Expression<String> lowerExpr = mock(Expression.class);

      // use doReturn to avoid generics-related stubbing issues with Mockito
      doReturn(casePath).when(root).get(ClaimSpecification.CASE_REFERENCE_NUMBER);
      when(cb.lower(casePath)).thenReturn(lowerExpr);

      String expectedPattern = "%" + caseReferenceNumber.toLowerCase() + "%";
      when(cb.like(lowerExpr, expectedPattern)).thenReturn(predicate2);

      // build a request-based model to call the request-based filterBy
      ClaimSearchRequest requestModel =
          ClaimSearchRequest.builder()
              .officeCode(officeCode)
              .caseReferenceNumber(caseReferenceNumber)
              .build();

      Specification<Claim> spec = ClaimSpecification.filterBy(requestModel);

      // when
      Predicate result = spec.toPredicate(root, query, cb);

      // then
      assertThat(result).isNotNull();
      verify(root).join(ClaimSpecification.SUBMISSION_ENTITY);
      verify(cb).lower(casePath);
      verify(cb).like(lowerExpr, expectedPattern);
    }
  }

  @Nested
  @DisplayName("orderByTotalWarningMessages(Pageable)")
  class OrderByTotalWarningMessagesTest {

    @Test
    @DisplayName("with null pageable returns conjunction")
    void orderByTotalWarningMessagesWithNullPageableReturnsConjunction() {
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
    @DisplayName("with no sort returns conjunction")
    void orderByTotalWarningMessagesWithNoSortReturnsConjunction() {
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
    @DisplayName("with non-matching sort property does not alter query")
    void orderByTotalWarningMessagesWithNonMatchingSortPropertyDoesNotAlterQuery() {
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
    @DisplayName("with totalWarnings sort adds subquery order by")
    void orderByTotalWarningMessagesWithTotalWarningsSortAddsSubqueryOrderBy() {
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
    }
  }

  @Nested
  @DisplayName("orderBySubmissionPeriod(Pageable")
  class OrderBySubmissionPeriodTest {

    @Test
    @DisplayName("with null pageable returns conjunction")
    void orderBySubmissionPeriodWithNullPageableReturnsConjunction() {
      // given
      Pageable pageable = null;
      when(cb.conjunction()).thenReturn(predicate1);

      Specification<Claim> spec = ClaimSpecification.orderBySubmissionPeriod(pageable);

      // when
      Predicate result = spec.toPredicate(root, query, cb);

      // then
      assertThat(result).isEqualTo(predicate1);
      verify(cb).conjunction();
      verifyNoMoreInteractions(query);
    }

    @Test
    @DisplayName("with unsorted pageable returns conjunction")
    void orderBySubmissionPeriodWithUnsortedPageableReturnsConjunction() {
      // given
      Pageable pageable = PageRequest.of(0, 10, Sort.unsorted());
      when(cb.conjunction()).thenReturn(predicate1);

      Specification<Claim> spec = ClaimSpecification.orderBySubmissionPeriod(pageable);

      // when
      Predicate result = spec.toPredicate(root, query, cb);

      // then
      assertThat(result).isEqualTo(predicate1);
      verify(cb).conjunction();
      verifyNoMoreInteractions(query);
    }

    @Test
    @DisplayName("with non-matching sort property does nothing")
    void orderBySubmissionPeriodWithNonMatchingSortPropertyDoesNothing() {
      // given
      Pageable pageable = PageRequest.of(0, 10, Sort.by("anotherField"));
      when(cb.conjunction()).thenReturn(predicate1);

      Specification<Claim> spec = ClaimSpecification.orderBySubmissionPeriod(pageable);

      // when
      Predicate result = spec.toPredicate(root, query, cb);

      // then
      assertThat(result).isEqualTo(predicate1);
      verify(cb).conjunction();
      verifyNoMoreInteractions(query);
    }

    @Test
    @DisplayName("with matching sort property adds order by clause")
    void orderBySubmissionPeriodWithMatchingSortPropertyAddsOrderByClause() {
      // given
      Pageable pageable =
          PageRequest.of(0, 10, Sort.by(Sort.Order.asc("submission.submissionPeriod")));
      when(cb.conjunction()).thenReturn(predicate1);

      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);

      Expression<Date> dateExpr = mock(Expression.class);
      when(cb.function(eq("to_date"), eq(Date.class), any(), any())).thenReturn(dateExpr);

      // when
      Specification<Claim> spec = ClaimSpecification.orderBySubmissionPeriod(pageable);
      Predicate result = spec.toPredicate(root, query, cb);

      // then
      assertThat(result).isEqualTo(predicate1);

      verify(root).join(ClaimSpecification.SUBMISSION_ENTITY);
      verify(cb).function(eq("to_date"), eq(Date.class), any(), any());
    }
  }

  @Nested
  @DisplayName("orderByDerivedClaimStatus(Pageable)")
  class OrderByDerivedClaimStatusTest {

    @Test
    @DisplayName("with null pageable returns conjunction")
    void orderByDerivedClaimStatusWithNullPageableReturnsConjunction() {
      Pageable pageable = null;
      when(cb.conjunction()).thenReturn(predicate1);

      Specification<Claim> spec = ClaimSpecification.orderByDerivedClaimStatus(pageable);

      Predicate result = spec.toPredicate(root, query, cb);

      assertThat(result).isEqualTo(predicate1);
      verify(cb).conjunction();
      verifyNoMoreInteractions(query);
    }

    @Test
    @DisplayName("with matching sort builds case expression")
    void orderByDerivedClaimStatusWithMatchingSortBuildsCaseExpression() {
      Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.asc("derivedClaimStatus")));
      when(cb.conjunction()).thenReturn(predicate1);

      // Mock the selectCase() chain to return an Expression<Integer>
      CriteriaBuilder.Case<Integer> caseBuilder =
          mock(CriteriaBuilder.Case.class, org.mockito.Answers.RETURNS_SELF);
      Expression<Integer> derivedExpr = mock(Expression.class);

      // Ensure cb.equal(...) and cb.isTrue(...) return non-null predicates so the chained
      // caseBuilder.when(...) calls receive non-null arguments (Mockito matchers do not match
      // null by default).
      Predicate anyPredicate = mock(Predicate.class);
      when(cb.equal(any(), any())).thenReturn(anyPredicate);
      when(cb.isTrue(any())).thenReturn(anyPredicate);

      when(cb.<Integer>selectCase()).thenReturn(caseBuilder);
      // The chained when(...) calls can return the same caseBuilder instance. Use doReturn to avoid
      // calling the real method on the mock during stubbing (which would return null).
      // Note: the when(...) method takes an Expression (Predicate extends Expression<Boolean>), so
      // match against Expression to ensure the stub is applied.
      // The RETURNS_SELF mock will cause chained when(...) calls to return the same mock instance.
      // Ensure the mock is returned for selectCase()
      // No explicit doReturn for when() is required because of RETURNS_SELF.
      // finally, the otherwise(...) call should return an Expression
      doReturn(derivedExpr).when(caseBuilder).otherwise(anyInt());

      Specification<Claim> spec = ClaimSpecification.orderByDerivedClaimStatus(pageable);
      Predicate result = spec.toPredicate(root, query, cb);

      assertThat(result).isEqualTo(predicate1);
      verify(cb).selectCase();
      verify(caseBuilder).otherwise(anyInt());
      verify(cb).asc(root.get(ClaimSpecification.ID));
    }
  }

  @Nested
  @DisplayName("orderByLatestCalculatedFee(Pageable)")
  class OrderByLatestCalculatedFeeTest {

    @Test
    @DisplayName("with null pageable returns conjunction")
    void orderByLatestCalculatedFeeWithNullPageableReturnsConjunction() {
      Pageable pageable = null;
      when(cb.conjunction()).thenReturn(predicate1);

      Specification<Claim> spec = ClaimSpecification.orderByLatestCalculatedFee(pageable);

      Predicate result = spec.toPredicate(root, query, cb);

      assertThat(result).isEqualTo(predicate1);
      verify(cb).conjunction();
      verifyNoMoreInteractions(query);
    }

    @Test
    @DisplayName("with matching property builds subqueries and orders")
    void orderByLatestCalculatedFeeWithMatchingPropertyBuildsSubqueriesAndOrders() {
      // given
      String sortProp = ClaimSpecification.CALCULATED_FEE_DETAILS + ".totalAmount";
      Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.asc(sortProp)));

      when(cb.conjunction()).thenReturn(predicate1);

      // Prepare latestFeeValueSubquery
      Subquery<Object> latestFeeSubquery = mock(Subquery.class);
      Root<CalculatedFeeDetail> feeRoot = mock(Root.class);

      when(query.subquery(Object.class)).thenReturn(latestFeeSubquery);
      when(latestFeeSubquery.from(CalculatedFeeDetail.class)).thenReturn(feeRoot);

      // Prepare newerRecordSubquery
      Subquery<Integer> newerRecordSubquery = mock(Subquery.class);
      Root<CalculatedFeeDetail> newerFeeRoot = mock(Root.class);

      when(query.subquery(Integer.class)).thenReturn(newerRecordSubquery);
      when(newerRecordSubquery.from(CalculatedFeeDetail.class)).thenReturn(newerFeeRoot);

      // Mock literal select
      Expression<Integer> literalExpr = mock(Expression.class);
      when(cb.literal(1)).thenReturn(literalExpr);
      when(newerRecordSubquery.select(literalExpr)).thenReturn(newerRecordSubquery);

      // Predicates for newerRecordSubquery.where(...)
      Predicate newerEqualPredicate = mock(Predicate.class);
      Predicate greaterThanPredicate = mock(Predicate.class);
      Predicate equalCreatedOnPredicate = mock(Predicate.class);
      Predicate greaterIdPredicate = mock(Predicate.class);
      Predicate andPredicate = mock(Predicate.class);
      Predicate orPredicate = mock(Predicate.class);

      when(cb.equal(
              newerFeeRoot.get(ClaimSpecification.CLAIM_ENTITY),
              feeRoot.get(ClaimSpecification.CLAIM_ENTITY)))
          .thenReturn(newerEqualPredicate);
      when(cb.greaterThan(
              newerFeeRoot.get(ClaimSpecification.CREATED_ON),
              feeRoot.get(ClaimSpecification.CREATED_ON)))
          .thenReturn(greaterThanPredicate);
      when(cb.equal(
              newerFeeRoot.get(ClaimSpecification.CREATED_ON),
              feeRoot.get(ClaimSpecification.CREATED_ON)))
          .thenReturn(equalCreatedOnPredicate);
      when(cb.greaterThan(
              newerFeeRoot.get(ClaimSpecification.ID), feeRoot.get(ClaimSpecification.ID)))
          .thenReturn(greaterIdPredicate);

      when(cb.and(equalCreatedOnPredicate, greaterIdPredicate)).thenReturn(andPredicate);
      when(cb.or(greaterThanPredicate, andPredicate)).thenReturn(orPredicate);

      when(newerRecordSubquery.where(newerEqualPredicate, orPredicate))
          .thenReturn(newerRecordSubquery);

      // latestFeeSubquery.select(feeRoot.get(feeFieldName))
      when(latestFeeSubquery.select(feeRoot.get("totalAmount"))).thenReturn(latestFeeSubquery);

      // cb.exists(newerRecordSubquery) and cb.not(...)
      Predicate existsPredicate = mock(Predicate.class);
      Predicate notExistsPredicate = mock(Predicate.class);
      when(cb.exists(newerRecordSubquery)).thenReturn(existsPredicate);
      when(cb.not(existsPredicate)).thenReturn(notExistsPredicate);

      when(latestFeeSubquery.where(
              cb.equal(feeRoot.get(ClaimSpecification.CLAIM_ENTITY), root), notExistsPredicate))
          .thenReturn(latestFeeSubquery);

      // when
      Specification<Claim> spec = ClaimSpecification.orderByLatestCalculatedFee(pageable);
      Predicate result = spec.toPredicate(root, query, cb);

      // then
      assertThat(result).isEqualTo(predicate1);
      verify(query).subquery(Object.class);
      verify(query).subquery(Integer.class);
      // The ordering should use the latestFeeSubquery as an expression
      verify(cb).asc(root.get(ClaimSpecification.ID));
    }

    @Test
    @DisplayName("with descending primary sort preserves id asc tie-break")
    void orderByLatestCalculatedFeeWithDescendingPrimaryPreservesIdAscTieBreak() {
      // given
      String sortProp = ClaimSpecification.CALCULATED_FEE_DETAILS + ".totalAmount";
      Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc(sortProp)));

      when(cb.conjunction()).thenReturn(predicate1);

      // Prepare latestFeeValueSubquery
      Subquery<Object> latestFeeSubquery = mock(Subquery.class);
      Root<CalculatedFeeDetail> feeRoot = mock(Root.class);

      when(query.subquery(Object.class)).thenReturn(latestFeeSubquery);
      when(latestFeeSubquery.from(CalculatedFeeDetail.class)).thenReturn(feeRoot);

      // Prepare newerRecordSubquery
      Subquery<Integer> newerRecordSubquery = mock(Subquery.class);
      Root<CalculatedFeeDetail> newerFeeRoot = mock(Root.class);

      when(query.subquery(Integer.class)).thenReturn(newerRecordSubquery);
      when(newerRecordSubquery.from(CalculatedFeeDetail.class)).thenReturn(newerFeeRoot);

      // Mock literal select
      Expression<Integer> literalExpr = mock(Expression.class);
      when(cb.literal(1)).thenReturn(literalExpr);
      when(newerRecordSubquery.select(literalExpr)).thenReturn(newerRecordSubquery);

      // Predicates for newerRecordSubquery.where(...)
      Predicate newerEqualPredicate = mock(Predicate.class);
      Predicate greaterThanPredicate = mock(Predicate.class);
      Predicate equalCreatedOnPredicate = mock(Predicate.class);
      Predicate greaterIdPredicate = mock(Predicate.class);
      Predicate andPredicate = mock(Predicate.class);
      Predicate orPredicate = mock(Predicate.class);

      when(cb.equal(
              newerFeeRoot.get(ClaimSpecification.CLAIM_ENTITY),
              feeRoot.get(ClaimSpecification.CLAIM_ENTITY)))
          .thenReturn(newerEqualPredicate);
      when(cb.greaterThan(
              newerFeeRoot.get(ClaimSpecification.CREATED_ON),
              feeRoot.get(ClaimSpecification.CREATED_ON)))
          .thenReturn(greaterThanPredicate);
      when(cb.equal(
              newerFeeRoot.get(ClaimSpecification.CREATED_ON),
              feeRoot.get(ClaimSpecification.CREATED_ON)))
          .thenReturn(equalCreatedOnPredicate);
      when(cb.greaterThan(
              newerFeeRoot.get(ClaimSpecification.ID), feeRoot.get(ClaimSpecification.ID)))
          .thenReturn(greaterIdPredicate);

      when(cb.and(equalCreatedOnPredicate, greaterIdPredicate)).thenReturn(andPredicate);
      when(cb.or(greaterThanPredicate, andPredicate)).thenReturn(orPredicate);

      when(newerRecordSubquery.where(newerEqualPredicate, orPredicate))
          .thenReturn(newerRecordSubquery);

      // latestFeeSubquery.select(feeRoot.get(feeFieldName))
      when(latestFeeSubquery.select(feeRoot.get("totalAmount"))).thenReturn(latestFeeSubquery);

      // cb.exists(newerRecordSubquery) and cb.not(...)
      Predicate existsPredicate = mock(Predicate.class);
      Predicate notExistsPredicate = mock(Predicate.class);
      when(cb.exists(newerRecordSubquery)).thenReturn(existsPredicate);
      when(cb.not(existsPredicate)).thenReturn(notExistsPredicate);

      when(latestFeeSubquery.where(
              cb.equal(feeRoot.get(ClaimSpecification.CLAIM_ENTITY), root), notExistsPredicate))
          .thenReturn(latestFeeSubquery);

      // when
      Specification<Claim> spec = ClaimSpecification.orderByLatestCalculatedFee(pageable);
      Predicate result = spec.toPredicate(root, query, cb);

      // then
      assertThat(result).isEqualTo(predicate1);
      verify(query).subquery(Object.class);
      verify(query).subquery(Integer.class);
      // The ordering should use DESC for the primary expression and still use ID ASC as tie-break
      verify(cb).desc(any(Expression.class));
      verify(cb).asc(root.get(ClaimSpecification.ID));
    }
  }
}
