package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimInterestedDepartment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.GovernmentDepartmentRef;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.InquestDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimBadRequestException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.InquestDetailMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimAmendmentPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimInquestDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimInterestedDepartmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.GovernmentDepartmentRefRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.InquestDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@Service
@RequiredArgsConstructor
public class ClaimInquestService {

  private final ClaimsApiProperties claimsApiProperties;
  private final ClientRepository clientRepository;
  private final InquestDetailRepository inquestDetailRepository;
  private final InquestDetailMapper inquestDetailMapper;
  private final GovernmentDepartmentRefRepository governmentDepartmentRefRepository;
  private final ClaimInterestedDepartmentRepository claimInterestedDepartmentRepository;

  public void saveInquestData(Claim claim, ClaimPost claimPost) {
    InquestDetail inquestDetail = inquestDetailMapper.toInquestDetail(claimPost.getInquestDetail());
    if (hasInquestDetailData(inquestDetail)) {
      inquestDetail.setId(Uuid7.timeBasedUuid());
      inquestDetail.setClaim(claim);
      inquestDetail.setCreatedByUserId(claimPost.getCreatedByUserId());
      inquestDetailRepository.save(inquestDetail);
    }

    saveInterestedDepartments(claim, claimPost);
  }

  public ClaimInquestDetail getClaimInquestDetailForResponse(UUID claimId) {
    return claimsApiProperties.getInquests().isEnabled() ? getClaimInquestDetail(claimId) : null;
  }

  public void clearIsInquestIfFlagDisabled(FeeCalculationPatch feeCalculationResponse) {
    if (!claimsApiProperties.getInquests().isEnabled() && feeCalculationResponse != null) {
      feeCalculationResponse.setIsInquest(null);
    }
  }

  public Map<UUID, ClaimInquestDetail> getClaimInquestDetails(
      List<UUID> claimIds, Map<UUID, Client> clientsByClaimId) {
    if (claimIds.isEmpty()) {
      return Map.of();
    }

    Map<UUID, ClaimInquestDetail> detailsByClaimId =
        claimIds.stream()
            .collect(Collectors.toMap(claimId -> claimId, claimId -> new ClaimInquestDetail()));

    inquestDetailRepository
        .findByClaimIdIn(claimIds)
        .forEach(
            entity ->
                detailsByClaimId.put(
                    entity.getClaim().getId(), inquestDetailMapper.toClaimInquestDetail(entity)));

    clientsByClaimId.forEach(
        (claimId, client) ->
            detailsByClaimId.get(claimId).setIsClientMeansTested(client.getIsMeansTested()));

    Map<UUID, List<String>> departmentsByClaimId =
        claimInterestedDepartmentRepository
            .findByClaimIdInOrderByClaimIdAscDisplayOrderAsc(claimIds)
            .stream()
            .collect(
                Collectors.groupingBy(
                    department -> department.getClaim().getId(),
                    Collectors.mapping(
                        department -> department.getGovernmentDepartment().getDisplayLabel(),
                        Collectors.toList())));
    detailsByClaimId.forEach(
        (claimId, detail) ->
            detail.setInterestedDepartments(departmentsByClaimId.getOrDefault(claimId, List.of())));

    return detailsByClaimId;
  }

  private ClaimInquestDetail getClaimInquestDetail(UUID claimId) {
    ClaimInquestDetail claimInquestDetail =
        inquestDetailRepository
            .findByClaimId(claimId)
            .map(inquestDetailMapper::toClaimInquestDetail)
            .orElseGet(ClaimInquestDetail::new);

    clientRepository
        .findByClaimId(claimId)
        .ifPresent(client -> claimInquestDetail.setIsClientMeansTested(client.getIsMeansTested()));

    List<String> departments =
        claimInterestedDepartmentRepository.findByClaimIdOrderByDisplayOrderAsc(claimId).stream()
            .map(ClaimInterestedDepartment::getGovernmentDepartment)
            .map(GovernmentDepartmentRef::getDisplayLabel)
            .toList();
    claimInquestDetail.setInterestedDepartments(departments);

    return claimInquestDetail;
  }

  public void rejectInquestPostWhenDisabled(ClaimPost claimPost) {
    if (!claimsApiProperties.getInquests().isEnabled() && claimPost.getInquestDetail() != null) {
      throw new ClaimBadRequestException("Inquest fields are not currently enabled.");
    }
  }

  public void ignoreIsInquestPatchWhenDisabled(ClaimAmendmentPatch claimPatch) {
    if (claimsApiProperties.getInquests().isEnabled()
        || claimPatch.getFeeCalculationResponse() == null) {
      return;
    }

    FeeCalculationPatch feeCalculationResponse = claimPatch.getFeeCalculationResponse();
    feeCalculationResponse.setIsInquest(null);
  }

  private void saveInterestedDepartments(Claim claim, ClaimPost claimPost) {
    ClaimInquestDetail claimInquestDetail = claimPost.getInquestDetail();
    if (claimInquestDetail == null || claimInquestDetail.getInterestedDepartments() == null) {
      return;
    }

    int displayOrder = 1;
    for (String departmentName : claimInquestDetail.getInterestedDepartments()) {
      if (!StringUtils.hasText(departmentName)) {
        continue;
      }
      GovernmentDepartmentRef governmentDepartment =
          governmentDepartmentRefRepository
              .findByDisplayLabelIgnoreCase(departmentName.trim())
              .orElseThrow(
                  () ->
                      new ClaimBadRequestException(
                          String.format(
                              "Unknown interested government department: %s", departmentName)));

      claimInterestedDepartmentRepository.save(
          ClaimInterestedDepartment.builder()
              .id(Uuid7.timeBasedUuid())
              .claim(claim)
              .governmentDepartment(governmentDepartment)
              .displayOrder(displayOrder++)
              .createdByUserId(claimPost.getCreatedByUserId())
              .build());
    }
  }

  private boolean hasInquestDetailData(InquestDetail inquestDetail) {
    return inquestDetail != null
        && (StringUtils.hasText(inquestDetail.getDeceasedForename())
            || StringUtils.hasText(inquestDetail.getDeceasedSurname())
            || inquestDetail.getDeceasedDateOfDeath() != null
            || StringUtils.hasText(inquestDetail.getCoronersInquestReference()));
  }
}
