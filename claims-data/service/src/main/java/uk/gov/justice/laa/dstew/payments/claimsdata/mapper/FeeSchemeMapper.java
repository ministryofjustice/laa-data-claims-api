package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import java.math.BigDecimal;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.fee.scheme.model.BoltOnType;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationRequest;

/** Maps submission and claim data into requests for the Fee Scheme Platform API. */
@Mapper(componentModel = "spring")
public interface FeeSchemeMapper {

  /**
   * Maps a claim to a fee calculation request to send to the Fee Scheme Platform API.
   *
   * @param claim the claim to map
   * @return a {@code FeeCalculationRequest} representing the claim
   */
  // claimId is the property on ClaimStateSnapshot (was incorrectly referencing "id")
  @Mapping(target = "claimId", source = "claimId")
  @Mapping(target = "feeCode", source = "feeCode")
  @Mapping(target = "startDate", source = "caseStartDate")
  @Mapping(target = "netProfitCosts", source = "netProfitCostsAmount")
  @Mapping(target = "netDisbursementAmount", source = "netDisbursementAmount")
  @Mapping(target = "netCostOfCounsel", source = "netCounselCostsAmount")
  @Mapping(target = "disbursementVatAmount", source = "disbursementsVatAmount")
  @Mapping(target = "vatIndicator", source = "isVatApplicable")
  @Mapping(target = "immigrationPriorAuthorityNumber", source = "priorAuthorityReference")
  @Mapping(target = "policeStationId", source = "policeStationCourtPrisonId")
  @Mapping(target = "policeStationSchemeId", source = "schemeId")
  @Mapping(target = "boltOns", source = "claim")
  @Mapping(target = "detentionTravelAndWaitingCosts", source = "detentionTravelWaitingCostsAmount")
  @Mapping(target = "caseConcludedDate", source = "caseConcludedDate")
  @Mapping(target = "uniqueFileNumber", source = "uniqueFileNumber")
  @Mapping(target = "numberOfMediationSessions", source = "mediationSessionsCount")
  @Mapping(target = "jrFormFilling", source = "jrFormFillingAmount")
  @Mapping(target = "londonRate", source = "isLondonRate", defaultValue = "false")
  FeeCalculationRequest mapToFeeCalculationRequest(
      ClaimStateSnapshot claim, @Context AreaOfLaw areaOfLaw);

  /**
   * Performs area-of-law specific adjustments after MapStruct has mapped common fields.
   *
   * @param feeCalculationRequest the target request being populated
   * @param claim the source claim containing cost amounts
   * @param areaOfLaw the area of law context used to decide which adjustments to apply
   */
  @AfterMapping
  default void applyPostMappingAdjustments(
      @MappingTarget FeeCalculationRequest feeCalculationRequest,
      ClaimStateSnapshot claim,
      @Context AreaOfLaw areaOfLaw) {
    switch (areaOfLaw) {
      case CRIME_LOWER -> {
        feeCalculationRequest.setNetTravelCosts(toDouble(claim.getTravelWaitingCostsAmount()));
        feeCalculationRequest.setNetWaitingCosts(toDouble(claim.getNetWaitingCostsAmount()));
      }
      case LEGAL_HELP ->
          feeCalculationRequest.setTravelAndWaitingCosts(
              toDouble(claim.getTravelWaitingCostsAmount()));
      default -> {
        // No area-of-law specific adjustments needed
      }
    }
  }

  private Double toDouble(BigDecimal value) {
    return value != null ? value.doubleValue() : null;
  }

  /**
   * Map claim fields to an object holding the bolt ons.
   *
   * @param claim the claim to map
   * @return a {@code BoltOnType} representing the claim
   */
  @Mapping(
      target = "boltOnAdjournedHearing",
      source = "adjournedHearingFeeAmount") // TODO: Mismatch in types Integer <-> BigDecimal
  @Mapping(target = "boltOnCmrhOral", source = "cmrhOralCount")
  @Mapping(target = "boltOnCmrhTelephone", source = "cmrhTelephoneCount")
  @Mapping(target = "boltOnHomeOfficeInterview", source = "hoInterview")
  @Mapping(target = "boltOnSubstantiveHearing", source = "isSubstantiveHearing")
  BoltOnType mapToBoltOnType(ClaimStateSnapshot claim);
}
