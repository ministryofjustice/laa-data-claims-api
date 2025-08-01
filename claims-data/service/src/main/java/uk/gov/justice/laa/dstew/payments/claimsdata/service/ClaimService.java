package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.javers.core.Javers;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimRequestBody;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;

/**
 * Service class for handling claims requests.
 */
@RequiredArgsConstructor
@Service
public class ClaimService {

  public static final String API_USER = "api user"; //TODO: replace with the actual user ID/name when available
  private final ClaimRepository claimRepository;
  private final ClaimMapper claimMapper;
  private final Javers javers;

  /**
   * Gets all claims.
   *
   * @return the list of claims
   */
  public List<Claim> getAllClaims() {
    return claimRepository.findAll().stream().map(claimMapper::toClaim).toList();
  }

  /**
   * Gets an claim for a given id.
   *
   * @param id the claim id
   * @return the requested claim
   */
  public Claim getClaim(Long id) {
    ClaimEntity claimEntity = checkIfClaimExist(id);
    return claimMapper.toClaim(claimEntity);
  }

  /**
   * Creates an claim.
   *
   * @param claimRequestBody the claim to be created
   * @return the id of the created claim
   */
  public Long createClaim(ClaimRequestBody claimRequestBody) {
    ClaimEntity claimEntity = new ClaimEntity();
    claimEntity.setName(claimRequestBody.getName());
    claimEntity.setDescription(claimRequestBody.getDescription());
    ClaimEntity createdClaimEntity = claimRepository.save(claimEntity);
    javers.commit(API_USER, createdClaimEntity);
    return createdClaimEntity.getId();
  }

  /**
   * Updates an claim.
   *
   * @param id the id of the claim to be updated
   * @param claimRequestBody the updated claim
   */
  public void updateClaim(Long id, ClaimRequestBody claimRequestBody) {
    ClaimEntity claimEntity = checkIfClaimExist(id);
    claimEntity.setName(claimRequestBody.getName());
    claimEntity.setDescription(claimRequestBody.getDescription());
    claimRepository.save(claimEntity);
    javers.commit(API_USER, claimEntity);
  }

  /**
   * Deletes an claim.
   *
   * @param id the id of the claim to be deleted
   */
  public void deleteClaim(Long id) {
    ClaimEntity claimToDelete = checkIfClaimExist(id);
    javers.commitShallowDelete(API_USER, claimToDelete);

    claimRepository.deleteById(id);
  }

  private ClaimEntity checkIfClaimExist(Long id) {
    return claimRepository
        .findById(id)
        .orElseThrow(
            () -> new ClaimNotFoundException(String.format("No claim found with id: %s", id)));
  }
}
