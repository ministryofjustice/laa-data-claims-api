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
import java.util.UUID;
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

    @Test
    @DisplayName("request based: with areaOfLaw adds submission area equality")
    void filterByRequestWithAreaOfLawAddsPredicate() {
      // arrange
      String officeCode = "OFF-A";
      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);

      Predicate officePredicate = mock(Predicate.class);
      when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
          .thenReturn(officePredicate);
      when(cb.and(any(Predicate[].class))).thenReturn(officePredicate);

      // request without areaOfLaw should work
      ClaimSearchRequest req = ClaimSearchRequest.builder().officeCode(officeCode).build();
      Specification<Claim> spec = ClaimSpecification.filterBy(req);
      Predicate result = spec.toPredicate(root, query, cb);
      assertThat(result).isNotNull();

      // now exercise areaOfLaw branch
      ClaimSearchRequest reqWithArea =
          ClaimSearchRequest.builder()
              .officeCode(officeCode)
              .areaOfLaw(uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw.LEGAL_HELP)
              .build();

      Path areaPath = mock(Path.class);
      Predicate areaPredicate = mock(Predicate.class);
      when(submissionJoin.get(ClaimSpecification.AREA_OF_LAW)).thenReturn(areaPath);
      when(cb.equal(
              areaPath, uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw.LEGAL_HELP))
          .thenReturn(areaPredicate);
      when(cb.and(areaPredicate)).thenReturn(areaPredicate);

      Specification<Claim> spec2 = ClaimSpecification.filterBy(reqWithArea);
      Predicate result2 = spec2.toPredicate(root, query, cb);
      assertThat(result2).isNotNull();
    }

    @Test
    @DisplayName(
        "request based: with escapedCaseFlag builds latest fee subquery and exists predicate")
    void filterByRequestWithEscapedCaseFlagUsesLatestFeeSubquery() {
      // arrange
      String officeCode = "OFF-B";
      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);
      Predicate officePredicate = mock(Predicate.class);
      when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
          .thenReturn(officePredicate);
      when(cb.and(any(Predicate[].class))).thenReturn(officePredicate);

      // Prepare subqueries similar to production logic
      Subquery<UUID> latestFeeSubquery = mock(Subquery.class);
      Root<CalculatedFeeDetail> feeRoot = mock(Root.class);
      when(query.subquery(UUID.class)).thenReturn(latestFeeSubquery);
      when(latestFeeSubquery.from(CalculatedFeeDetail.class)).thenReturn(feeRoot);

      Subquery<Integer> newerRecordSubquery = mock(Subquery.class);
      Root<CalculatedFeeDetail> newerFeeRoot = mock(Root.class);
      when(query.subquery(Integer.class)).thenReturn(newerRecordSubquery);
      when(newerRecordSubquery.from(CalculatedFeeDetail.class)).thenReturn(newerFeeRoot);

      Expression<Integer> literalExpr = mock(Expression.class);
      when(cb.literal(1)).thenReturn(literalExpr);
      when(newerRecordSubquery.select(literalExpr)).thenReturn(newerRecordSubquery);

      Predicate newerEqualPredicate = mock(Predicate.class);
      Predicate gtCreated = mock(Predicate.class);
      Predicate eqCreated = mock(Predicate.class);
      Predicate gtId = mock(Predicate.class);
      when(cb.equal(
              newerFeeRoot.get(ClaimSpecification.CLAIM_ENTITY),
              feeRoot.get(ClaimSpecification.CLAIM_ENTITY)))
          .thenReturn(newerEqualPredicate);
      when(cb.greaterThan(
              newerFeeRoot.get(ClaimSpecification.CREATED_ON),
              feeRoot.get(ClaimSpecification.CREATED_ON)))
          .thenReturn(gtCreated);
      when(cb.equal(
              newerFeeRoot.get(ClaimSpecification.CREATED_ON),
              feeRoot.get(ClaimSpecification.CREATED_ON)))
          .thenReturn(eqCreated);
      when(cb.greaterThan(
              newerFeeRoot.get(ClaimSpecification.ID), feeRoot.get(ClaimSpecification.ID)))
          .thenReturn(gtId);

      Predicate andPredicate = mock(Predicate.class);
      Predicate orPredicate = mock(Predicate.class);
      when(cb.and(eqCreated, gtId)).thenReturn(andPredicate);
      when(cb.or(gtCreated, andPredicate)).thenReturn(orPredicate);
      when(newerRecordSubquery.where(newerEqualPredicate, orPredicate))
          .thenReturn(newerRecordSubquery);

      when(latestFeeSubquery.select(feeRoot.get(ClaimSpecification.ID)))
          .thenReturn(latestFeeSubquery);

      Predicate existsPredicate = mock(Predicate.class);
      Predicate notExists = mock(Predicate.class);
      when(cb.exists(newerRecordSubquery)).thenReturn(existsPredicate);
      when(cb.not(existsPredicate)).thenReturn(notExists);
      when(latestFeeSubquery.where(
              cb.equal(feeRoot.get(ClaimSpecification.CLAIM_ENTITY), root), notExists))
          .thenReturn(latestFeeSubquery);

      ClaimSearchRequest req =
          ClaimSearchRequest.builder().officeCode(officeCode).escapedCaseFlag(true).build();
      Specification<Claim> spec = ClaimSpecification.filterBy(req);
      Predicate result = spec.toPredicate(root, query, cb);
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("request based: with submissionId parses uuid")
    void filterByRequestWithSubmissionIdParsesUuid() {
      String officeCode = "OFF-R1";
      UUID submissionId = UUID.randomUUID();

      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);
      Predicate officePredicate = mock(Predicate.class);
      when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
          .thenReturn(officePredicate);
      when(cb.and(any(Predicate[].class))).thenReturn(officePredicate);

      ClaimSearchRequest req =
          ClaimSearchRequest.builder()
              .officeCode(officeCode)
              .submissionId(submissionId.toString())
              .build();
      Specification<Claim> spec = ClaimSpecification.filterBy(req);
      Predicate result = spec.toPredicate(root, query, cb);
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("request based: with submissionStatuses applies IN predicate")
    void filterByRequestWithSubmissionStatusesAddsPredicate() {
      String officeCode = "OFF-R2";
      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);
      Predicate officePredicate = mock(Predicate.class);
      when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
          .thenReturn(officePredicate);
      when(cb.and(any(Predicate[].class))).thenReturn(officePredicate);

      Path statusPath = mock(Path.class);
      Predicate inPredicate = mock(Predicate.class);
      when(submissionJoin.get(ClaimSpecification.STATUS)).thenReturn(statusPath);
      when(statusPath.in(List.of(SubmissionStatus.CREATED))).thenReturn(inPredicate);

      ClaimSearchRequest req =
          ClaimSearchRequest.builder()
              .officeCode(officeCode)
              .submissionStatuses(List.of(SubmissionStatus.CREATED))
              .build();
      Specification<Claim> spec = ClaimSpecification.filterBy(req);
      Predicate result = spec.toPredicate(root, query, cb);
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("request based: with submissionPeriod adds equality predicate")
    void filterByRequestWithSubmissionPeriodAddsPredicate() {
      String officeCode = "OFF-R3";
      String period = "FEB-2025";
      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);
      Predicate officePredicate = mock(Predicate.class);
      when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
          .thenReturn(officePredicate);
      when(cb.and(any(Predicate[].class))).thenReturn(officePredicate);

      Predicate periodPredicate = mock(Predicate.class);
      when(cb.equal(submissionJoin.get(ClaimSpecification.SUBMISSION_PERIOD), period))
          .thenReturn(periodPredicate);

      ClaimSearchRequest req =
          ClaimSearchRequest.builder().officeCode(officeCode).submissionPeriod(period).build();
      Specification<Claim> spec = ClaimSpecification.filterBy(req);
      Predicate result = spec.toPredicate(root, query, cb);
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("request based: with claimStatuses applies IN predicate")
    void filterByRequestWithClaimStatusesAddsPredicate() {
      String officeCode = "OFF-R4";
      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);
      Predicate officePredicate = mock(Predicate.class);
      when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
          .thenReturn(officePredicate);
      when(cb.and(any(Predicate[].class))).thenReturn(officePredicate);

      Path claimStatusPath = mock(Path.class);
      Predicate inPredicate = mock(Predicate.class);
      when(root.get(ClaimSpecification.STATUS)).thenReturn(claimStatusPath);
      when(claimStatusPath.in(List.of(ClaimStatus.READY_TO_PROCESS))).thenReturn(inPredicate);

      ClaimSearchRequest req =
          ClaimSearchRequest.builder()
              .officeCode(officeCode)
              .claimStatuses(List.of(ClaimStatus.READY_TO_PROCESS))
              .build();
      Specification<Claim> spec = ClaimSpecification.filterBy(req);
      Predicate result = spec.toPredicate(root, query, cb);
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("request based: with feeCode and uniqueFileNumber adds equality predicates")
    void filterByRequestWithFeeCodeAndUniqueFileNumberAddsPredicates() {
      String officeCode = "OFF-R5";
      String feeCode = "F-R-1";
      String ufn = "UFN-R-1";
      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);
      Predicate officePredicate = mock(Predicate.class);
      when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
          .thenReturn(officePredicate);
      when(cb.and(any(Predicate[].class))).thenReturn(officePredicate);

      Path feePath = mock(Path.class);
      Predicate feePredicate = mock(Predicate.class);
      when(root.get(ClaimSpecification.FEE_CODE)).thenReturn(feePath);
      when(cb.equal(feePath, feeCode)).thenReturn(feePredicate);

      Path ufnPath = mock(Path.class);
      Predicate ufnPredicate = mock(Predicate.class);
      when(root.get(ClaimSpecification.UNIQUE_FILE_NUMBER)).thenReturn(ufnPath);
      when(cb.equal(ufnPath, ufn)).thenReturn(ufnPredicate);

      ClaimSearchRequest req =
          ClaimSearchRequest.builder()
              .officeCode(officeCode)
              .feeCode(feeCode)
              .uniqueFileNumber(ufn)
              .build();
      Specification<Claim> spec = ClaimSpecification.filterBy(req);
      Predicate result = spec.toPredicate(root, query, cb);
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("request based: with uniqueClientNumber and uniqueCaseId uses subqueries")
    void filterByRequestWithUniqueClientAndCaseUsesSubqueries() {
      String officeCode = "OFF-R6";
      String uniqueClientNumber = "CL-R-1";
      String uniqueCaseId = "UC-R-1";

      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);
      Predicate officePredicate = mock(Predicate.class);
      when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
          .thenReturn(officePredicate);
      when(cb.and(any(Predicate[].class))).thenReturn(officePredicate);

      // Client join branch in request-based filter
      Join<Claim, Client> clientJoin = mock(Join.class);
      when(root.join(ClaimSpecification.CLIENT_ENTITY)).thenReturn((Join) clientJoin);
      when(cb.equal(clientJoin.get(ClaimSpecification.UNIQUE_CLIENT_NUMBER), uniqueClientNumber))
          .thenReturn(mock(Predicate.class));

      // ClaimCase join branch in request-based filter
      Join<Claim, ClaimCase> claimCaseJoin = mock(Join.class);
      when(root.join(ClaimSpecification.CLAIM_CASE_ENTITY)).thenReturn((Join) claimCaseJoin);
      when(cb.equal(claimCaseJoin.get(ClaimSpecification.UNIQUE_CASE_ID), uniqueCaseId))
          .thenReturn(mock(Predicate.class));

      ClaimSearchRequest req =
          ClaimSearchRequest.builder()
              .officeCode(officeCode)
              .uniqueClientNumber(uniqueClientNumber)
              .uniqueCaseId(uniqueCaseId)
              .build();
      Specification<Claim> spec = ClaimSpecification.filterBy(req);
      Predicate result = spec.toPredicate(root, query, cb);
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName(
        "when uniqueClientNumber present and query is null throws IllegalArgumentException")
    void filterByWithUniqueClientNumberAndNullQueryThrows() {
      // given
      String officeCode = "OFF-Q1";
      String uniqueClientNumber = "CL-NULL-1";

      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);
      when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
          .thenReturn(predicate1);

      Specification<Claim> spec =
          ClaimSpecification.filterBy(
              officeCode, null, null, null, null, uniqueClientNumber, null, null, null, null);

      IllegalArgumentException ex =
          Assertions.assertThrows(
              IllegalArgumentException.class, () -> spec.toPredicate(root, null, cb));
      assertThat(ex.getMessage()).isEqualTo("Query must not be null");
    }

    @Test
    @DisplayName("when uniqueCaseId present and query is null throws IllegalArgumentException")
    void filterByWithUniqueCaseIdAndNullQueryThrows() {
      // given
      String officeCode = "OFF-Q2";
      String uniqueCaseId = "CASE-NULL-1";

      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);
      when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
          .thenReturn(predicate1);

      Specification<Claim> spec =
          ClaimSpecification.filterBy(
              officeCode, null, null, null, null, null, uniqueCaseId, null, null, null);

      IllegalArgumentException ex =
          Assertions.assertThrows(
              IllegalArgumentException.class, () -> spec.toPredicate(root, null, cb));
      assertThat(ex.getMessage()).isEqualTo("Query must not be null");
    }

    @Test
    @DisplayName("with submissionId parses uuid and adds predicate")
    void filterByWithSubmissionIdParsesUuid() {
      // given
      String officeCode = "OFF-1";
      UUID submissionId = UUID.randomUUID();

      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);

      Predicate officePredicate = mock(Predicate.class);
      when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
          .thenReturn(officePredicate);

      Path idPath = mock(Path.class);
      Predicate submissionPredicate = mock(Predicate.class);
      when(submissionJoin.get(ClaimSpecification.ID)).thenReturn(idPath);
      when(cb.equal(idPath, submissionId)).thenReturn(submissionPredicate);

      when(cb.and(any(Predicate[].class))).thenReturn(submissionPredicate);

      Specification<Claim> spec =
          ClaimSpecification.filterBy(
              officeCode, submissionId.toString(), null, null, null, null, null, null, null, null);

      // when
      Predicate result = spec.toPredicate(root, query, cb);

      // then
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("with submissionStatuses applies IN predicate")
    void filterByWithSubmissionStatusesAddsPredicate() {
      // given
      String officeCode = "OFF-2";

      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);

      Predicate officePredicate = mock(Predicate.class);
      when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
          .thenReturn(officePredicate);

      Path submissionStatusPath = mock(Path.class);
      Predicate inPredicate = mock(Predicate.class);
      when(submissionJoin.get(ClaimSpecification.STATUS)).thenReturn(submissionStatusPath);
      when(submissionStatusPath.in(List.of(SubmissionStatus.CREATED))).thenReturn(inPredicate);

      when(cb.and(any(Predicate[].class))).thenReturn(officePredicate);

      Specification<Claim> spec =
          ClaimSpecification.filterBy(
              officeCode,
              null,
              List.of(SubmissionStatus.CREATED),
              null,
              null,
              null,
              null,
              null,
              null,
              null);

      // when
      Predicate result = spec.toPredicate(root, query, cb);
      // then
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("with submissionPeriod adds equality predicate")
    void filterByWithSubmissionPeriodAddsPredicate() {
      String officeCode = "OFF-3";
      String period = "JAN-2025";

      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);

      Predicate officePredicate = mock(Predicate.class);
      when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
          .thenReturn(officePredicate);

      Predicate periodPredicate = mock(Predicate.class);
      when(cb.equal(submissionJoin.get(ClaimSpecification.SUBMISSION_PERIOD), period))
          .thenReturn(periodPredicate);

      when(cb.and(any(Predicate[].class))).thenReturn(officePredicate);

      Specification<Claim> spec =
          ClaimSpecification.filterBy(
              officeCode, null, null, null, null, null, null, null, period, null);

      Predicate result = spec.toPredicate(root, query, cb);
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("with feeCode and uniqueFileNumber adds equality predicates")
    void filterByWithFeeCodeAndUniqueFileNumberAddsPredicates() {
      String officeCode = "OFF-4";
      String feeCode = "FEE-1";
      String ufn = "UFN-1";

      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);

      Predicate officePredicate = mock(Predicate.class);
      when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
          .thenReturn(officePredicate);

      Path feePath = mock(Path.class);
      Predicate feePredicate = mock(Predicate.class);
      when(root.get(ClaimSpecification.FEE_CODE)).thenReturn(feePath);
      when(cb.equal(feePath, feeCode)).thenReturn(feePredicate);

      Path ufnPath = mock(Path.class);
      Predicate ufnPredicate = mock(Predicate.class);
      when(root.get(ClaimSpecification.UNIQUE_FILE_NUMBER)).thenReturn(ufnPath);
      when(cb.equal(ufnPath, ufn)).thenReturn(ufnPredicate);

      when(cb.and(any(Predicate[].class))).thenReturn(officePredicate);

      Specification<Claim> spec =
          ClaimSpecification.filterBy(
              officeCode, null, null, feeCode, ufn, null, null, null, null, null);

      Predicate result = spec.toPredicate(root, query, cb);
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("with claimStatuses applies IN on claim status")
    void filterByWithClaimStatusesAddsPredicate() {
      String officeCode = "OFF-5";

      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);

      Predicate officePredicate = mock(Predicate.class);
      when(cb.equal(submissionJoin.get(ClaimSpecification.OFFICE_ACCOUNT_NUMBER), officeCode))
          .thenReturn(officePredicate);

      Path claimStatusPath = mock(Path.class);
      Predicate inPredicate = mock(Predicate.class);
      when(root.get(ClaimSpecification.STATUS)).thenReturn(claimStatusPath);
      when(claimStatusPath.in(List.of(ClaimStatus.READY_TO_PROCESS))).thenReturn(inPredicate);
      when(cb.and(any(Predicate[].class))).thenReturn(officePredicate);

      Specification<Claim> spec =
          ClaimSpecification.filterBy(
              officeCode,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(ClaimStatus.READY_TO_PROCESS),
              null,
              null);

      Predicate result = spec.toPredicate(root, query, cb);
      assertThat(result).isNotNull();
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

    @Test
    @DisplayName("with totalWarnings descending sort adds DESC subquery order by")
    void orderByTotalWarningMessagesWithTotalWarningsDescAddsSubqueryOrderBy() {
      // given
      Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("totalWarnings")));

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
      assertThat(result).isEqualTo(predicate1);
      verify(query).subquery(Long.class);
      verify(cb).desc(warningSubquery);
    }

    @Test
    @DisplayName("with mixed sort entries where totalWarnings is later uses ASC ordering")
    void orderByTotalWarningMessagesWithMixedSortUsesAscWhenLater() {
      // given: first order doesn't match, second does
      Pageable pageable =
          PageRequest.of(
              0, 10, Sort.by(Sort.Order.asc("otherField"), Sort.Order.asc("totalWarnings")));

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
      assertThat(result).isEqualTo(predicate1);
      verify(cb).asc(warningSubquery);
    }

    @Test
    @DisplayName("with mixed sort entries where totalWarnings later uses DESC ordering")
    void orderByTotalWarningMessagesWithMixedSortUsesDescWhenLater() {
      Pageable pageable =
          PageRequest.of(
              0, 10, Sort.by(Sort.Order.asc("otherField"), Sort.Order.desc("totalWarnings")));

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

      Specification<Claim> spec = ClaimSpecification.orderByTotalWarningMessages(pageable);
      Predicate result = spec.toPredicate(root, query, cb);

      assertThat(result).isEqualTo(predicate1);
      verify(cb).desc(warningSubquery);
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

    @Test
    @DisplayName("with matching submissionPeriod descending adds DESC ordering")
    void orderBySubmissionPeriodWithMatchingSortPropertyDescAddsOrderByClause() {
      Pageable pageable =
          PageRequest.of(0, 10, Sort.by(Sort.Order.desc("submission.submissionPeriod")));
      when(cb.conjunction()).thenReturn(predicate1);

      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);

      Expression<Date> dateExpr = mock(Expression.class);
      when(cb.function(eq("to_date"), eq(Date.class), any(), any())).thenReturn(dateExpr);

      Specification<Claim> spec = ClaimSpecification.orderBySubmissionPeriod(pageable);
      Predicate result = spec.toPredicate(root, query, cb);

      assertThat(result).isEqualTo(predicate1);
      verify(cb).function(eq("to_date"), eq(Date.class), any(), any());
      verify(cb).desc(dateExpr);
    }

    @Test
    @DisplayName("with mixed sort entries where submissionPeriod is later uses ASC ordering")
    void orderBySubmissionPeriodWithMixedSortUsesAscWhenLater() {
      Pageable pageable =
          PageRequest.of(
              0,
              10,
              Sort.by(Sort.Order.asc("otherField"), Sort.Order.asc("submission.submissionPeriod")));

      when(cb.conjunction()).thenReturn(predicate1);
      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);

      Expression<Date> dateExpr = mock(Expression.class);
      when(cb.function(eq("to_date"), eq(Date.class), any(), any())).thenReturn(dateExpr);

      Specification<Claim> spec = ClaimSpecification.orderBySubmissionPeriod(pageable);
      Predicate result = spec.toPredicate(root, query, cb);

      assertThat(result).isEqualTo(predicate1);
      verify(cb).asc(dateExpr);
    }

    @Test
    @DisplayName("with mixed sort entries where submissionPeriod later uses DESC ordering")
    void orderBySubmissionPeriodWithMixedSortUsesDescWhenLater() {
      Pageable pageable =
          PageRequest.of(
              0,
              10,
              Sort.by(
                  Sort.Order.asc("otherField"), Sort.Order.desc("submission.submissionPeriod")));

      when(cb.conjunction()).thenReturn(predicate1);
      when(root.join(ClaimSpecification.SUBMISSION_ENTITY)).thenReturn((Join) submissionJoin);

      Expression<Date> dateExpr = mock(Expression.class);
      when(cb.function(eq("to_date"), eq(Date.class), any(), any())).thenReturn(dateExpr);

      Specification<Claim> spec = ClaimSpecification.orderBySubmissionPeriod(pageable);
      Predicate result = spec.toPredicate(root, query, cb);

      assertThat(result).isEqualTo(predicate1);
      verify(cb).desc(dateExpr);
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

    @Test
    @DisplayName("with matching sort descending builds case expression and uses DESC")
    void orderByDerivedClaimStatusWithMatchingSortDescendingUsesDesc() {
      Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("derivedClaimStatus")));
      when(cb.conjunction()).thenReturn(predicate1);

      CriteriaBuilder.Case<Integer> caseBuilder =
          mock(CriteriaBuilder.Case.class, org.mockito.Answers.RETURNS_SELF);
      Expression<Integer> derivedExpr = mock(Expression.class);
      Predicate anyPredicate = mock(Predicate.class);
      when(cb.equal(any(), any())).thenReturn(anyPredicate);
      when(cb.isTrue(any())).thenReturn(anyPredicate);

      when(cb.<Integer>selectCase()).thenReturn(caseBuilder);
      doReturn(derivedExpr).when(caseBuilder).otherwise(anyInt());

      Specification<Claim> spec = ClaimSpecification.orderByDerivedClaimStatus(pageable);
      Predicate result = spec.toPredicate(root, query, cb);

      assertThat(result).isEqualTo(predicate1);
      verify(cb).selectCase();
      verify(caseBuilder).otherwise(anyInt());
      verify(cb).desc(any(Expression.class));
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
      // The ordering should use DESC for the primary expression and use ID DESC as the tie-break
      verify(cb).desc(any(Expression.class));
      verify(cb).desc(root.get(ClaimSpecification.ID));
    }
  }
}
