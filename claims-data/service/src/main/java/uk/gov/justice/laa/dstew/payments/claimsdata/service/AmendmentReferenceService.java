package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.AmendmentReferenceMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendmentRequestedByReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendmentRequestedByReferenceList;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AmendmentReasonReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.RequestedByReferenceRepository;

/**
 * Read-only service exposing governed amendment reference data: the active Requested By values,
 * each with the active Amendment Reason values valid for that requesting party, in display order.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AmendmentReferenceService {

  private final RequestedByReferenceRepository requestedByReferenceRepository;
  private final AmendmentReasonReferenceRepository amendmentReasonReferenceRepository;
  private final AmendmentReferenceMapper amendmentReferenceMapper;

  /**
   * Builds the amendment reference lookup payload. Inactive Requested By values and inactive
   * reasons are excluded. Reasons are nested under their Requested By value in display order.
   *
   * @return the amendment Requested By reference list with nested reasons
   */
  @Transactional(readOnly = true)
  public AmendmentRequestedByReferenceList getAmendmentRequestedByReferences() {
    List<RequestedByReference> requestedByValues =
        requestedByReferenceRepository.findByIsActiveTrueOrderByDisplayOrderAsc();

    Map<String, List<AmendmentReasonReference>> reasonsByRequestedByCode =
        amendmentReasonReferenceRepository
            .findByIsActiveTrueOrderByRequestedByCodeAscDisplayOrderAsc()
            .stream()
            .collect(
                Collectors.groupingBy(
                    AmendmentReasonReference::getRequestedByCode, Collectors.toList()));

    List<AmendmentRequestedByReference> requestedBy =
        requestedByValues.stream()
            .map(
                entity -> {
                  AmendmentRequestedByReference model =
                      amendmentReferenceMapper.toRequestedByModel(entity);
                  model.setReasons(
                      reasonsByRequestedByCode.getOrDefault(entity.getCode(), List.of()).stream()
                          .map(amendmentReferenceMapper::toReasonModel)
                          .toList());
                  return model;
                })
            .toList();

    return new AmendmentRequestedByReferenceList().requestedBy(requestedBy);
  }
}
