package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClientMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ValidatedClaimSummaryMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidatedClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidatedClaimSummary;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimCaseRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification.ClaimSpecification;

/** Service containing business logic for handling amendments. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AmendmentService {
  private final ClaimRepository claimRepository;
  private final ValidatedClaimSummaryMapper claimSummaryMapper;
  private final ClaimSummaryFeeRepository claimSummaryFeeRepository;
  private final CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  private final ClaimCaseRepository claimCaseRepository;
  private final ClientRepository clientRepository;
  private final ClaimMapper claimMapper;
  private final ClientMapper clientMapper;

  /**
   * Returns all the existing claims filtered by some parameters and paginated in a {@link
   * ValidatedClaimSummary}.
   *
   * @param officeCode a mandatory string representing an office code to filter claims by
   * @param uniqueFileNumber the optional unique file number associated to the claim to filter
   *     claims by
   * @param uniqueClientNumber the optional unique client number associated to the claim to filter
   *     claims by
   * @param uniqueCaseId TODO
   * @param submitted TODO
   * @param pageable a pageable object to yield the paginated claims results
   * @return the paginated result set with all claims that satisfy the filtering criteria above.
   */
  public ValidatedClaimSummary getValidClaimSet(
      String officeCode,
      String uniqueFileNumber,
      String uniqueClientNumber,
      String uniqueCaseId,
      LocalDate submitted,
      Pageable pageable) {

    Page<Claim> page =
        claimRepository.findAll(
            ClaimSpecification.filterBy(
                officeCode, uniqueFileNumber, uniqueClientNumber, uniqueCaseId, submitted),
            pageable);

    ValidatedClaimSummary response = claimSummaryMapper.toValidatedClaimSummary(page);

    for (ValidatedClaimResponse claimResponse : response.getContent()) {
      if (claimResponse.getId() != null) {
        clientRepository
            .findByClaimId(UUID.fromString(claimResponse.getId()))
            .ifPresent(
                client ->
                    clientMapper.updateValidatedClaimResponseFromClient(client, claimResponse));
        claimSummaryFeeRepository
            .findByClaimId(UUID.fromString(claimResponse.getId()))
            .ifPresent(
                fee ->
                    claimMapper.updateValidatedClaimResponseFromClaimSummaryFee(
                        fee, claimResponse));
        calculatedFeeDetailRepository
            .findByClaimId(UUID.fromString(claimResponse.getId()))
            .ifPresent(
                feeDetail ->
                    claimMapper.updateValidatedClaimResponseFromCalculatedFeeDetail(
                        feeDetail, claimResponse));
        claimCaseRepository
            .findByClaimId(UUID.fromString(claimResponse.getId()))
            .ifPresent(
                claimCase ->
                    claimMapper.updateValidatedClaimResponseFromClaimCase(
                        claimCase, claimResponse));
      }
    }
    return response;
  }
}
