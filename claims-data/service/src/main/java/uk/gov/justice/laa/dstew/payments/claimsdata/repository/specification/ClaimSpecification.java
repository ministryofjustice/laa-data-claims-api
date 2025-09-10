package uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;

/**
 * This class provide basic filtering logic to query {@link Claim} entities using JPA {@link
 * Specification}.
 *
 * <p>The resulting specification can be used with Spring Data JPA repositories to fetch filtered
 * claim records from the database.
 */
public final class ClaimSpecification {

  /**
   * Constructs a JPA {@link Specification} for filtering {@link Claim} records based on various
   * parameters. The resulting specification can be used to dynamically generate predicates for
   * querying claim.
   *
   * @param officeCode a mandatory string representing an office code to filter claims by
   * @param submissionId an optional identifier to filter claims by
   * @param submissionStatuses an optional list of submission statuses to filter claims by
   * @param feeCode an optional string representing a fee code to filter claims by
   * @param uniqueFileNumber the optional unique file number associated to the claim to filter
   *     claims by
   * @param uniqueClientNumber the optional unique client number associated to the claim to filter
   *     claims by
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
      List<ClaimStatus> claimStatuses) {

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

      // Join with Client
      if (StringUtils.hasText(uniqueClientNumber)) {
        // Subquery to check existence of matching clients
        assert query != null;
        Subquery<Client> clientSubquery = query.subquery(Client.class);
        Root<Client> clientRoot = clientSubquery.from(Client.class);
        clientSubquery
            .select(clientRoot.get("id"))
            .where(
                cb.equal(clientRoot.get("claim"), root),
                cb.equal(clientRoot.get("uniqueClientNumber"), uniqueClientNumber));

        predicates.add(cb.exists(clientSubquery));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
