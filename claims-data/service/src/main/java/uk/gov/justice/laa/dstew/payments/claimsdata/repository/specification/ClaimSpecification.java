package uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.ClaimSearchRequest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.DerivedClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.DerivedClaimStatusResolver;

/**
 * This class provide basic filtering logic to query {@link Claim} entities using JPA {@link
 * Specification}.
 *
 * <p>The resulting specification can be used with Spring Data JPA repositories to fetch filtered
 * claim records from the database.
 */
public final class ClaimSpecification {

  private static final String NOT_NULL_QUERY_MESSAGE = "Query must not be null";
  public static final String OFFICE_ACCOUNT_NUMBER = "officeAccountNumber";
  public static final String ID = "id";
  public static final String STATUS = "status";
  public static final String SUBMISSION_PERIOD = "submissionPeriod";
  public static final String FEE_CODE = "feeCode";
  public static final String UNIQUE_FILE_NUMBER = "uniqueFileNumber";
  public static final String CASE_REFERENCE_NUMBER = "caseReferenceNumber";
  public static final String AREA_OF_LAW = "areaOfLaw";
  public static final String ESCAPE_CASE_FLAG = "escapeCaseFlag";
  public static final String UNIQUE_CASE_ID = "uniqueCaseId";
  public static final String CLIENT_ENTITY = "client";
  public static final String CLAIM_CASE_ENTITY = "claimCase";
  public static final String SUBMISSION_ENTITY = "submission";
  public static final String UNIQUE_CLIENT_NUMBER = "uniqueClientNumber";
  public static final String CLAIM_ENTITY = "claim";
  public static final String CREATED_ON = "createdOn";
  public static final String HAS_ASSESSMENT = "hasAssessment";
  public static final String IS_AMENDED = "isAmended";
  public static final String DERIVED_CLAIM_STATUS_SORT_KEY = "derivedClaimStatus";

  /**
   * Constructs a JPA {@link Specification} for filtering {@link Claim} records based on various
   * parameters. The resulting specification can be used to dynamically generate predicates for
   * querying claims.
   *
   * @param officeCode a mandatory string representing an office code to filter claims by
   * @param submissionId an optional identifier to filter claims by
   * @param submissionStatuses an optional list of submission statuses to filter claims by
   * @param feeCode an optional string representing a fee code to filter claims by
   * @param uniqueFileNumber the optional unique file number associated to the claim to filter
   *     claims by
   * @param uniqueClientNumber the optional unique client number associated to the claim to filter
   *     claims by
   * @param uniqueCaseId the optional unique case id associated to the claim to filter * claims by
   * @param claimStatuses an optional list of claim statuses to filter claims by
   * @return a JPA {@code Specification} of {@code Submission} containing the constructed filtering
   *     predicates
   */
  public static Specification<Claim> filterBy(
      String officeCode,
      String submissionId,
      List<SubmissionStatus> submissionStatuses,
      String feeCode,
      String uniqueFileNumber,
      String uniqueClientNumber,
      String uniqueCaseId,
      List<ClaimStatus> claimStatuses,
      String submissionPeriod,
      String caseReferenceNumber) {

    return (Root<Claim> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
      // Join with Submission
      Join<Claim, Submission> submissionJoin = root.join(SUBMISSION_ENTITY);

      List<Predicate> predicates = new ArrayList<>();

      // Filter on Submission fields
      predicates.add(cb.and(cb.equal(submissionJoin.get(OFFICE_ACCOUNT_NUMBER), officeCode)));

      if (StringUtils.hasText(submissionId)) {
        predicates.add(cb.and(cb.equal(submissionJoin.get(ID), UUID.fromString(submissionId))));
      }

      if (submissionStatuses != null && !submissionStatuses.isEmpty()) {
        predicates.add(cb.and(submissionJoin.get(STATUS).in(submissionStatuses)));
      }

      if (StringUtils.hasText(submissionPeriod)) {
        predicates.add(cb.and(cb.equal(submissionJoin.get(SUBMISSION_PERIOD), submissionPeriod)));
      }

      // Filter on Claim fields
      if (claimStatuses != null && !claimStatuses.isEmpty()) {
        predicates.add(cb.and(root.get(STATUS).in(claimStatuses)));
      }

      if (StringUtils.hasText(feeCode)) {
        predicates.add(cb.and(cb.equal(root.get(FEE_CODE), feeCode)));
      }

      if (StringUtils.hasText(uniqueFileNumber)) {
        predicates.add(cb.and(cb.equal(root.get(UNIQUE_FILE_NUMBER), uniqueFileNumber)));
      }

      if (StringUtils.hasText(caseReferenceNumber)) {
        predicates.add(cb.and(cb.equal(root.get(CASE_REFERENCE_NUMBER), caseReferenceNumber)));
      }

      // Filter on Client fields
      if (StringUtils.hasText(uniqueClientNumber)) {
        // Subquery to check existence of matching clients
        Assert.notNull(query, NOT_NULL_QUERY_MESSAGE);
        Subquery<Client> clientSubquery = getClientSubquery(uniqueClientNumber, root, query, cb);

        predicates.add(cb.exists(clientSubquery));
      }

      // Filter on Claim Case fields
      if (StringUtils.hasText(uniqueCaseId)) {
        // Subquery to check existence of matching claim cases
        Assert.notNull(query, NOT_NULL_QUERY_MESSAGE);
        Subquery<ClaimCase> claimCaseSubquery = getClaimCaseSubquery(uniqueCaseId, root, query, cb);

        predicates.add(cb.exists(claimCaseSubquery));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * Constructs a JPA {@link Specification} for filtering {@link Claim} records based on various
   * parameters. The resulting specification can be used to dynamically generate predicates for
   * querying claims.
   *
   * @param request containing the different values for the filters
   * @return a JPA {@code Specification} of {@code Submission} containing the constructed filtering
   *     predicates
   */
  public static Specification<Claim> filterBy(ClaimSearchRequest request) {

    return (Root<Claim> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      Join<Claim, Submission> submissionJoin = root.join(SUBMISSION_ENTITY);

      // Filter on Submission fields
      predicates.add(
          cb.and(cb.equal(submissionJoin.get(OFFICE_ACCOUNT_NUMBER), request.getOfficeCode())));

      if (StringUtils.hasText(request.getSubmissionId())) {
        predicates.add(
            cb.and(cb.equal(submissionJoin.get(ID), UUID.fromString(request.getSubmissionId()))));
      }

      if (request.getSubmissionStatuses() != null && !request.getSubmissionStatuses().isEmpty()) {
        predicates.add(cb.and(submissionJoin.get(STATUS).in(request.getSubmissionStatuses())));
      }

      if (StringUtils.hasText(request.getSubmissionPeriod())) {
        predicates.add(
            cb.and(cb.equal(submissionJoin.get(SUBMISSION_PERIOD), request.getSubmissionPeriod())));
      }

      if (Optional.ofNullable(request.getAreaOfLaw()).isPresent()) {
        predicates.add(cb.and(cb.equal(submissionJoin.get(AREA_OF_LAW), request.getAreaOfLaw())));
      }

      if (Optional.ofNullable(request.getEscapedCaseFlag()).isPresent()) {
        Subquery<UUID> latestFeeSubquery = query.subquery(UUID.class);
        Root<CalculatedFeeDetail> feeRoot = latestFeeSubquery.from(CalculatedFeeDetail.class);

        // Correlation: Look for a "newer" record than the current one we are inspecting
        Subquery<Integer> newerRecordSubquery = query.subquery(Integer.class);
        Root<CalculatedFeeDetail> newerFeeRoot =
            newerRecordSubquery.from(CalculatedFeeDetail.class);

        newerRecordSubquery
            .select(cb.literal(1))
            .where(
                cb.equal(newerFeeRoot.get(CLAIM_ENTITY), feeRoot.get(CLAIM_ENTITY)), // Same claim
                cb.or(
                    // Strategy: Either createdOn is strictly newer...
                    cb.greaterThan(newerFeeRoot.get(CREATED_ON), feeRoot.get(CREATED_ON)),
                    // ...or createdOn matches exactly, but the UUIDv7 string/value breaks the tie
                    cb.and(
                        cb.equal(newerFeeRoot.get(CREATED_ON), feeRoot.get(CREATED_ON)),
                        cb.greaterThan(newerFeeRoot.get(ID), feeRoot.get(ID)))));

        // Now assemble the main filter matching the original query block
        latestFeeSubquery
            .select(feeRoot.get(ID))
            .where(
                cb.equal(feeRoot.get(CLAIM_ENTITY), root), // Tied to parent claim
                cb.not(cb.exists(newerRecordSubquery)), // Guarantees feeRoot IS the latest record
                cb.equal(feeRoot.get(ESCAPE_CASE_FLAG), request.getEscapedCaseFlag()));

        predicates.add(cb.exists(latestFeeSubquery));
      }

      // Filter on Claim fields
      if (request.getClaimStatuses() != null && !request.getClaimStatuses().isEmpty()) {
        predicates.add(cb.and(root.get(STATUS).in(request.getClaimStatuses())));
      }

      if (StringUtils.hasText(request.getFeeCode())) {
        predicates.add(cb.and(cb.equal(root.get(FEE_CODE), request.getFeeCode())));
      }

      if (StringUtils.hasText(request.getUniqueFileNumber())) {
        predicates.add(
            cb.and(cb.equal(root.get(UNIQUE_FILE_NUMBER), request.getUniqueFileNumber())));
      }

      if (StringUtils.hasText(request.getCaseReferenceNumber())) {
        // Perform a case-insensitive 'contains' search so that partial CRNs (or different casing)
        // will match stored case reference numbers. Examples: search 'ABC' will match 'ABC-1234',
        // search 'ate2/1' will match 'RAC ATE2/1'. (DSTEW-1414)
        String pattern = "%" + request.getCaseReferenceNumber().toLowerCase() + "%";
        predicates.add(cb.and(cb.like(cb.lower(root.get(CASE_REFERENCE_NUMBER)), pattern)));
      }

      if (StringUtils.hasText(request.getUniqueClientNumber())) {
        Join<Claim, Client> clientJoin = root.join(CLIENT_ENTITY);
        predicates.add(
            cb.and(
                cb.equal(clientJoin.get(UNIQUE_CLIENT_NUMBER), request.getUniqueClientNumber())));
      }

      if (StringUtils.hasText(request.getUniqueCaseId())) {
        Join<Claim, ClaimCase> claimCaseJoin = root.join(CLAIM_CASE_ENTITY);
        predicates.add(
            cb.and(cb.equal(claimCaseJoin.get(UNIQUE_CASE_ID), request.getUniqueCaseId())));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static Subquery<Client> getClientSubquery(
      String uniqueClientNumber, Root<Claim> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
    Subquery<Client> clientSubquery = query.subquery(Client.class);
    Root<Client> clientRoot = clientSubquery.from(Client.class);
    clientSubquery
        .select(clientRoot.get(ID))
        .where(
            cb.equal(clientRoot.get(CLAIM_ENTITY), root),
            cb.equal(clientRoot.get(UNIQUE_CLIENT_NUMBER), uniqueClientNumber));
    return clientSubquery;
  }

  private static Subquery<ClaimCase> getClaimCaseSubquery(
      String uniqueCaseId, Root<Claim> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
    Subquery<ClaimCase> claimCaseSubquery = query.subquery(ClaimCase.class);
    Root<ClaimCase> claimCaseRoot = claimCaseSubquery.from(ClaimCase.class);
    claimCaseSubquery
        .select(claimCaseRoot.get(ID))
        .where(
            cb.equal(claimCaseRoot.get(CLAIM_ENTITY), root),
            cb.equal(claimCaseRoot.get(UNIQUE_CASE_ID), uniqueCaseId));
    return claimCaseSubquery;
  }

  /**
   * Constructs a JPA {@link Specification} for ordering {@link Claim} records by submissionPeriod,
   * which requires custom processing.
   *
   * @param pageable includes pagination info
   * @return a JPA {@code Specification} of {@code Submission} containing the constructed filtering
   *     predicates
   */
  public static Specification<Claim> orderBySubmissionPeriod(Pageable pageable) {
    return (root, query, cb) -> {
      if (pageable == null || pageable.getSort().isUnsorted()) {
        return cb.conjunction();
      }

      // Join the Submission entity because submissionPeriod lives there
      Join<Claim, Submission> submissionJoin = root.join(SUBMISSION_ENTITY);

      for (Sort.Order order : pageable.getSort()) {
        if (!"submission.submissionPeriod".equalsIgnoreCase(order.getProperty())) {
          continue;
        }

        Expression<Date> submissionPeriodAsDate =
            cb.function(
                "to_date",
                Date.class,
                submissionJoin.get(SUBMISSION_PERIOD),
                cb.literal("MON-YYYY"));

        query.orderBy(
            order.isAscending() ? cb.asc(submissionPeriodAsDate) : cb.desc(submissionPeriodAsDate),
            // Deterministic secondary sort so rows never drift between pages.
            cb.asc(root.get(ID)));

        break;
      }

      return cb.conjunction();
    };
  }

  /**
   * Constructs a JPA {@link Specification} for ordering {@link Claim} records by the count of total
   * warning validation messages.
   *
   * @param pageable includes pagination info
   * @return a JPA {@code Specification} of {@code Submission} containing the constructed filtering
   *     predicates
   */
  public static Specification<Claim> orderByTotalWarningMessages(Pageable pageable) {
    return (root, query, cb) -> {
      if (pageable == null || pageable.getSort().isUnsorted()) {
        return cb.conjunction();
      }

      for (Sort.Order order : pageable.getSort()) {
        if (!"totalWarnings".equalsIgnoreCase(order.getProperty())) {
          continue;
        }
        Subquery<Long> warningCountSubquery = query.subquery(Long.class);
        Root<ValidationMessageLog> vml = warningCountSubquery.from(ValidationMessageLog.class);

        warningCountSubquery
            .select(cb.count(vml))
            .where(
                cb.equal(vml.get("claimId"), root.get(ID)),
                cb.equal(vml.get("type"), ValidationMessageType.WARNING));

        query.orderBy(
            order.isAscending() ? cb.asc(warningCountSubquery) : cb.desc(warningCountSubquery),
            // Deterministic secondary sort so rows never drift between pages.
            cb.asc(root.get(ID)));

        // Only handle the first matching custom sort
        break;
      }

      // No extra predicate, only ordering
      return cb.conjunction();
    };
  }

  /**
   * Constructs a JPA {@link Specification} for ordering {@link Claim} records by their derived
   * business status ({@link DerivedClaimStatus}).
   *
   * <p>This is a computed sort: {@code derivedClaimStatus} is not a persisted column. Ordering is
   * applied via a SQL {@code CASE} expression whose ordinal outputs are taken from {@link
   * DerivedClaimStatus#ordinal()} (the enum declaration order is the canonical business ordering).
   * The {@code CASE} precedence mirrors {@link DerivedClaimStatusResolver} — the single Java source
   * of truth for the derivation — and the two are kept in lock-step by a parity test.
   *
   * <p>A deterministic secondary sort by {@code id} (ascending, UUIDv7) is always appended so that
   * claims sharing the same derived status keep a stable order across pages.
   *
   * <p><strong>Multi-field sort caveat:</strong> because JPA {@code query.orderBy(...)} replaces
   * the whole order list, this computed sort behaves as a single-field sort and must not be
   * combined with other sort fields in the same request; if combined, the ordering applied by
   * Spring Data from the remaining {@link Pageable} sort would override this specification's
   * ordering. See {@code docs/derived-claim-status.md} for how full computed multi-field sorting
   * could be added.
   *
   * @param pageable includes the requested sort orders
   * @return a JPA {@code Specification} that applies the derived-status ordering when requested
   */
  public static Specification<Claim> orderByDerivedClaimStatus(Pageable pageable) {
    return (root, query, cb) -> {
      if (pageable == null || pageable.getSort().isUnsorted()) {
        return cb.conjunction();
      }

      for (Sort.Order order : pageable.getSort()) {
        if (!DERIVED_CLAIM_STATUS_SORT_KEY.equalsIgnoreCase(order.getProperty())) {
          continue;
        }

        Expression<Integer> derivedOrdinal = derivedClaimStatusOrdinal(root, cb);

        query.orderBy(
            order.isAscending() ? cb.asc(derivedOrdinal) : cb.desc(derivedOrdinal),
            // Deterministic secondary sort so rows never drift between pages.
            cb.asc(root.get(ID)));

        // Only handle the first matching custom sort
        break;
      }

      return cb.conjunction();
    };
  }

  /**
   * Builds the {@code CASE} expression that maps each claim to its {@link DerivedClaimStatus}
   * ordinal. The precedence must mirror {@link DerivedClaimStatusResolver}; the ordinals are taken
   * from the enum so the canonical ordering is defined in exactly one place.
   */
  private static Expression<Integer> derivedClaimStatusOrdinal(
      Root<Claim> root, CriteriaBuilder cb) {
    return cb.<Integer>selectCase()
        .when(cb.equal(root.get(STATUS), ClaimStatus.VOID), DerivedClaimStatus.VOIDED.ordinal())
        .when(cb.equal(root.get(STATUS), ClaimStatus.INVALID), DerivedClaimStatus.INVALID.ordinal())
        .when(
            cb.equal(root.get(STATUS), ClaimStatus.READY_TO_PROCESS),
            DerivedClaimStatus.READY_TO_PROCESS.ordinal())
        .when(cb.isTrue(root.<Boolean>get(HAS_ASSESSMENT)), DerivedClaimStatus.ASSESSED.ordinal())
        .when(cb.isTrue(root.<Boolean>get(IS_AMENDED)), DerivedClaimStatus.AMENDED.ordinal())
        .otherwise(DerivedClaimStatus.ACCEPTED.ordinal());
  }
}
