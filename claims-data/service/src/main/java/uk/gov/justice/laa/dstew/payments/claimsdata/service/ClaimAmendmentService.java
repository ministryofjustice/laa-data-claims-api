package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendedField;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendmentStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimAmendmentPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimAmendmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;

/** Service for handling claim amendments. */
@Service
@RequiredArgsConstructor
public class ClaimAmendmentService {
  private final ClaimAmendmentRepository claimAmendmentRepository;
  private final ClaimRepository claimRepository;

  /**
   * Retrieves all amendments for a given claim.
   *
   * @param claimId the claim ID
   * @return list of ClaimAmendment
   */
  @Transactional(readOnly = true)
  public List<ClaimAmendment> getAmendmentsForClaim(UUID claimId) {
    return claimAmendmentRepository.findAll().stream()
        .filter(amendment -> amendment.getClaim().getId().equals(claimId))
        .toList();
  }

  /**
   * Updates the status of a claim amendment.
   *
   * @param claimId the claim ID
   * @param amendmentId the amendment ID
   * @param status the new status
   * @param updatedByUserId the user updating
   * @return optional ClaimAmendment if found and updated
   */
  @Transactional
  public Optional<ClaimAmendment> updateAmendmentStatus(
      UUID claimId, UUID amendmentId, AmendmentStatus status, String updatedByUserId) {
    Optional<ClaimAmendment> amendmentOpt = claimAmendmentRepository.findById(amendmentId);
    if (amendmentOpt.isPresent()) {
      ClaimAmendment amendment = amendmentOpt.get();
      if (!amendment.getClaim().getId().equals(claimId)) {
        return Optional.empty();
      }
      amendment.setStatus(status.getValue());
      amendment.setUpdatedByUserId(updatedByUserId);
      amendment.setUpdatedOn(OffsetDateTime.now());
      claimAmendmentRepository.save(amendment);
      return Optional.of(amendment);
    }
    return Optional.empty();
  }

  /**
   * Creates a new amendment for a claim.
   *
   * @param claimId the claim ID
   * @param post the amendment post data
   * @return the created ClaimAmendment
   */
  @Transactional
  public ClaimAmendment createAmendment(UUID claimId, ClaimAmendmentPost post) {
    Claim claim =
        claimRepository
            .findById(claimId)
            .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));
    ClaimAmendment amendment = new ClaimAmendment();
    amendment.setClaimAmendmentId(UUID.randomUUID());
    amendment.setClaim(claim);
    amendment.setCreatedByUserId(post.getCreatedByUserId());
    amendment.setStatus("pending");
    // Convert amendedFields to JSON string for changedFields
    try {
      amendment.setChangedFields(new ObjectMapper().writeValueAsString(post.getAmendedFields()));
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize amendedFields", e);
    }
    return claimAmendmentRepository.save(amendment);
  }

  /**
   * Converts a JSON string of changed fields to a list of AmendedField objects.
   *
   * @param changedFields the JSON string
   * @return list of AmendedField
   */
  public List<@Valid AmendedField> convertChangedFieldsToAmendedFields(String changedFields) {
    if (changedFields == null || changedFields.isEmpty()) {
      return List.of();
    }
    try {
      ObjectMapper mapper = new ObjectMapper();
      // AmendedField is a POJO, so we can deserialize as a list
      return List.of(mapper.readValue(changedFields, AmendedField[].class));
    } catch (Exception e) {
      throw new RuntimeException("Failed to deserialize changedFields to AmendedField list", e);
    }
  }
}
