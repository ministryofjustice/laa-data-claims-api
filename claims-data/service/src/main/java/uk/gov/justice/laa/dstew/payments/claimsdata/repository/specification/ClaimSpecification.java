package uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;

/**
 * This class provide basic filtering logic to query {@link Claim} entities using JPA {@link
 * Specification}.
 *
 * <p>The resulting specification can be used with Spring Data JPA repositories to fetch filtered
 * claim records from the database.
 */
public final class ClaimSpecification {

  private static final String NOT_NULL_QUERY_MESSAGE = "Query must not be null";

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
      Join<Claim, Submission> submissionJoin = root.join("submission");

      List<Predicate> predicates = new ArrayList<>();

      // Filter on Submission fields
      predicates.add(cb.and(cb.equal(submissionJoin.get("officeAccountNumber"), officeCode)));

      if (StringUtils.hasText(submissionId)) {
        predicates.add(cb.and(cb.equal(submissionJoin.get("id"), UUID.fromString(submissionId))));
      }

      if (submissionStatuses != null && !submissionStatuses.isEmpty()) {
        predicates.add(cb.and(submissionJoin.get("status").in(submissionStatuses)));
      }

      if (StringUtils.hasText(submissionPeriod)) {
        predicates.add(cb.and(cb.equal(submissionJoin.get("submissionPeriod"), submissionPeriod)));
      }

      // Filter on Claim fields
      if (claimStatuses != null && !claimStatuses.isEmpty()) {
        predicates.add(cb.and(root.get("status").in(claimStatuses)));
      }

      if (StringUtils.hasText(feeCode)) {
        predicates.add(cb.and(cb.equal(root.get("feeCode"), feeCode)));
      }

      if (StringUtils.hasText(uniqueFileNumber)) {
        predicates.add(cb.and(cb.equal(root.get("uniqueFileNumber"), uniqueFileNumber)));
      }

      if (StringUtils.hasText(caseReferenceNumber)) {
        predicates.add(cb.and(cb.equal(root.get("caseReferenceNumber"), caseReferenceNumber)));
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
      // Join with Submission
      Join<Claim, Submission> submissionJoin = root.join("submission");
      Join<Claim, CalculatedFeeDetail> calculatedFeeDetailJoin = root.join("calculatedFeeDetail");
      Join<Claim, Client> clientJoin = root.join("client");
      Join<Claim, ClaimCase> claimCaseJoin = root.join("claimCase");

      List<Predicate> predicates = new ArrayList<>();

      // Filter on Submission fields
      predicates.add(
          cb.and(cb.equal(submissionJoin.get("officeAccountNumber"), request.getOfficeCode())));

      if (StringUtils.hasText(request.getSubmissionId())) {
        predicates.add(
            cb.and(cb.equal(submissionJoin.get("id"), UUID.fromString(request.getSubmissionId()))));
      }

      if (request.getSubmissionStatuses() != null && !request.getSubmissionStatuses().isEmpty()) {
        predicates.add(cb.and(submissionJoin.get("status").in(request.getSubmissionStatuses())));
      }

      if (StringUtils.hasText(request.getSubmissionPeriod())) {
        predicates.add(
            cb.and(
                cb.equal(submissionJoin.get("submissionPeriod"), request.getSubmissionPeriod())));
      }

      if (Optional.ofNullable(request.getAreaOfLaw()).isPresent()) {
        predicates.add(cb.and(cb.equal(submissionJoin.get("areaOfLaw"), request.getAreaOfLaw())));
      }

      if (Optional.ofNullable(request.getEscapedCaseFlag()).isPresent()) {
        predicates.add(cb.and(cb.equal(calculatedFeeDetailJoin.get("escapeCaseFlag"), request.getEscapedCaseFlag())));
      }

      // Filter on Claim fields
      if (request.getClaimStatuses() != null && !request.getClaimStatuses().isEmpty()) {
        predicates.add(cb.and(root.get("status").in(request.getClaimStatuses())));
      }

      if (StringUtils.hasText(request.getFeeCode())) {
        predicates.add(cb.and(cb.equal(root.get("feeCode"), request.getFeeCode())));
      }

      if (StringUtils.hasText(request.getUniqueFileNumber())) {
        predicates.add(
            cb.and(cb.equal(root.get("uniqueFileNumber"), request.getUniqueFileNumber())));
      }

      if (StringUtils.hasText(request.getCaseReferenceNumber())) {
        predicates.add(
            cb.and(cb.equal(root.get("caseReferenceNumber"), request.getCaseReferenceNumber())));
      }

      // Filter on Client fields
//      if (StringUtils.hasText(request.getUniqueClientNumber())) {
//        // Subquery to check existence of matching clients
//        Assert.notNull(query, NOT_NULL_QUERY_MESSAGE);
//        Subquery<Client> clientSubquery =
//            getClientSubquery(request.getUniqueClientNumber(), root, query, cb);
//
//        predicates.add(cb.exists(clientSubquery));
//      }

      if (StringUtils.hasText(request.getUniqueClientNumber())) {
        predicates.add(cb.and(cb.equal(clientJoin.get("uniqueClientNumber"), request.getUniqueClientNumber())));
      }

      // Filter on Claim Case fields
//      if (StringUtils.hasText(request.getUniqueCaseId())) {
//        // Subquery to check existence of matching claim cases
//        Assert.notNull(query, NOT_NULL_QUERY_MESSAGE);
//        Subquery<ClaimCase> claimCaseSubquery =
//            getClaimCaseSubquery(request.getUniqueCaseId(), root, query, cb);
//
//        predicates.add(cb.exists(claimCaseSubquery));
//      }

      if (StringUtils.hasText(request.getUniqueCaseId())) {
        predicates.add(cb.and(cb.equal(claimCaseJoin.get("uniqueCaseId"), request.getUniqueCaseId())));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static Subquery<Client> getClientSubquery(
      String uniqueClientNumber, Root<Claim> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
    Subquery<Client> clientSubquery = query.subquery(Client.class);
    Root<Client> clientRoot = clientSubquery.from(Client.class);
    clientSubquery
        .select(clientRoot.get("id"))
        .where(
            cb.equal(clientRoot.get("claim"), root),
            cb.equal(clientRoot.get("uniqueClientNumber"), uniqueClientNumber));
    return clientSubquery;
  }

  private static Subquery<ClaimCase> getClaimCaseSubquery(
      String uniqueCaseId, Root<Claim> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
    Subquery<ClaimCase> claimCaseSubquery = query.subquery(ClaimCase.class);
    Root<ClaimCase> claimCaseRoot = claimCaseSubquery.from(ClaimCase.class);
    claimCaseSubquery
        .select(claimCaseRoot.get("id"))
        .where(
            cb.equal(claimCaseRoot.get("claim"), root),
            cb.equal(claimCaseRoot.get("uniqueCaseId"), uniqueCaseId));
    return claimCaseSubquery;
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
        if (!"total_warnings".equalsIgnoreCase(order.getProperty())) {
          continue;
        }

        // subquery: count(*) from validation_message_log where claim_id = claim.id and type =
        // 'WARNING'
        Subquery<Long> warningCountSubquery = query.subquery(Long.class);
        Root<ValidationMessageLog> vml = warningCountSubquery.from(ValidationMessageLog.class);

        warningCountSubquery
            .select(cb.count(vml))
            .where(
                cb.equal(vml.get("claimId"), root.get("id")),
                cb.equal(vml.get("type"), ValidationMessageType.WARNING));

        query.orderBy(
            order.isAscending() ? cb.asc(warningCountSubquery) : cb.desc(warningCountSubquery));

        // Only handle the first matching custom sort
        break;
      }

      // No extra predicate, only ordering
      return cb.conjunction();
    };
  }
}
