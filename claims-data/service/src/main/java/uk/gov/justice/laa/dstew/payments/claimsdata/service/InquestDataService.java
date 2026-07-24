package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimInterestedDepartment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimInterestedPublicAuthority;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.InquestDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimBadRequestException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimInquestData;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimInquestDataWrite;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimInterestedDepartmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimInterestedPublicAuthorityRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.DepartmentReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.InquestDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/** Creates, replaces and retrieves complete inquest data independently of claim status. */
@Service
@RequiredArgsConstructor
public class InquestDataService {
  private final ClaimRepository claimRepository;
  private final InquestDetailRepository detailRepository;
  private final DepartmentReferenceRepository departmentReferenceRepository;
  private final ClaimInterestedDepartmentRepository departmentRepository;
  private final ClaimInterestedPublicAuthorityRepository authorityRepository;
  private final InquestCompletenessDefinition completenessDefinition;

  /** Retrieves a claim's stored inquest data and current completeness evaluation. */
  @Transactional(readOnly = true)
  public ClaimInquestData get(UUID claimId) {
    requireClaim(claimId);
    return detailRepository
        .findByClaimId(claimId)
        .map(this::toModel)
        .orElseGet(
            () ->
                new ClaimInquestData()
                    .interestedDepartmentCodes(Set.of())
                    .interestedPublicAuthorities(List.of())
                    .actorUserId("")
                    .isComplete(false));
  }

  /** Creates inquest data, failing when the claim already has an inquest detail row. */
  @Transactional
  public ClaimInquestData create(UUID claimId, ClaimInquestDataWrite request) {
    if (detailRepository.findByClaimId(claimId).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Inquest data already exists");
    }
    return write(requireClaim(claimId), new InquestDetail(), request, false);
  }

  /** Atomically replaces all scalar and repeating inquest data for a claim. */
  @Transactional
  public ClaimInquestData replace(UUID claimId, ClaimInquestDataWrite request) {
    final InquestDetail detail =
        detailRepository
            .findByClaimId(claimId)
            .orElseThrow(() -> new ClaimNotFoundException("Inquest data not found"));
    departmentRepository.deleteByClaimId(claimId);
    authorityRepository.deleteByClaimId(claimId);
    departmentRepository.flush();
    authorityRepository.flush();
    return write(detail.getClaim(), detail, request, true);
  }

  private ClaimInquestData write(
      Claim claim, InquestDetail detail, ClaimInquestDataWrite request, boolean replacing) {
    Set<String> departmentCodes = request.getInterestedDepartmentCodes();
    if (departmentReferenceRepository.countByCodeIn(List.copyOf(departmentCodes))
        != departmentCodes.size()) {
      throw new ClaimBadRequestException("Unknown inquest department code");
    }
    Instant now = Instant.now();
    if (!replacing) {
      detail.setId(Uuid7.timeBasedUuid());
      detail.setClaim(claim);
      detail.setCreatedByUserId(request.getActorUserId());
      detail.setCreatedOn(now);
    } else {
      detail.setUpdatedByUserId(request.getActorUserId());
      detail.setUpdatedOn(now);
    }
    detail.setDeceasedForename(request.getDeceasedForename());
    detail.setDeceasedSurname(request.getDeceasedSurname());
    detail.setDeceasedDateOfBirth(request.getDeceasedDateOfBirth());
    detail.setDeceasedDateOfDeath(request.getDeceasedDateOfDeath());
    detail.setCoronersInquestReference(request.getCoronersInquestReference());
    detailRepository.save(detail);

    departmentRepository.saveAll(
        departmentCodes.stream()
            .map(code -> department(claim, code, request.getActorUserId(), now))
            .toList());
    for (int index = 0; index < request.getInterestedPublicAuthorities().size(); index++) {
      authorityRepository.save(
          authority(
              claim,
              request.getInterestedPublicAuthorities().get(index),
              index,
              request.getActorUserId(),
              now));
    }
    return toModel(detail);
  }

  private Claim requireClaim(UUID claimId) {
    return claimRepository
        .findById(claimId)
        .orElseThrow(() -> new ClaimNotFoundException("Claim not found"));
  }

  private ClaimInquestData toModel(InquestDetail detail) {
    List<String> departments =
        departmentRepository.findByClaimIdOrderByDepartmentCode(detail.getClaim().getId()).stream()
            .map(ClaimInterestedDepartment::getDepartmentCode)
            .toList();
    List<String> authorities =
        authorityRepository.findByClaimIdOrderByDisplayOrder(detail.getClaim().getId()).stream()
            .map(ClaimInterestedPublicAuthority::getAuthorityName)
            .toList();
    String actor =
        detail.getUpdatedByUserId() == null
            ? detail.getCreatedByUserId()
            : detail.getUpdatedByUserId();
    return new ClaimInquestData()
        .deceasedForename(detail.getDeceasedForename())
        .deceasedSurname(detail.getDeceasedSurname())
        .deceasedDateOfBirth(detail.getDeceasedDateOfBirth())
        .deceasedDateOfDeath(detail.getDeceasedDateOfDeath())
        .coronersInquestReference(detail.getCoronersInquestReference())
        .interestedDepartmentCodes(Set.copyOf(departments))
        .interestedPublicAuthorities(authorities)
        .actorUserId(actor)
        .isComplete(
            completenessDefinition.isComplete(detail, departments.size(), authorities.size()));
  }

  private ClaimInterestedDepartment department(
      Claim claim, String code, String actor, Instant now) {
    ClaimInterestedDepartment entity = new ClaimInterestedDepartment();
    entity.setId(Uuid7.timeBasedUuid());
    entity.setClaim(claim);
    entity.setDepartmentCode(code);
    entity.setCreatedByUserId(actor);
    entity.setCreatedOn(now);
    return entity;
  }

  private ClaimInterestedPublicAuthority authority(
      Claim claim, String name, int order, String actor, Instant now) {
    ClaimInterestedPublicAuthority entity = new ClaimInterestedPublicAuthority();
    entity.setId(Uuid7.timeBasedUuid());
    entity.setClaim(claim);
    entity.setAuthorityName(name);
    entity.setDisplayOrder(order);
    entity.setCreatedByUserId(actor);
    entity.setCreatedOn(now);
    return entity;
  }
}
