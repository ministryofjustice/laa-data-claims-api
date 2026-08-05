package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import java.util.Optional;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AssessmentSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.CalculatedFeeDetailSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

/**
 * Assembles an immutable {@link ClaimStateSnapshot} (the "before" state) from the persisted claim
 * aggregate.
 *
 * <p>Same-named, same-typed properties are mapped automatically by MapStruct. Explicit
 * {@code @Mapping}s cover: nested submission paths, and the property names that exist on more than
 * one source ({@code feeCode}/{@code schemeId} on claim vs calculated fee detail; {@code
 * netProfitCostsAmount}/{@code netWaitingCostsAmount}/{@code jrFormFillingAmount}/ {@code
 * isVatApplicable} on summary fee vs calculated fee detail/assessment) so the correct source wins.
 * Read-only calculated-fee and assessment context are mapped via the nested sub-mappers below.
 *
 * <p>Null-safe: MapStruct null-guards each source argument and nested path, so missing associations
 * leave the corresponding fields null. Performs no persistence and makes no external calls.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ClaimStateSnapshotMapper {

  /**
   * Builds a before-state snapshot from the supplied claim aggregate.
   *
   * @param claim the claim entity (required)
   * @param client the associated client, if any
   * @param claimCase the associated claim case, if any
   * @param summaryFee the associated claim summary fee, if any
   * @param calculatedFeeDetail the latest calculated fee detail, if any
   * @param latestAssessment the latest assessment, if any
   * @return an immutable snapshot of the current stored claim values
   */
  @Mapping(target = "claimId", source = "claim.id")
  @Mapping(target = "submissionId", source = "claim.submission.id")
  @Mapping(target = "areaOfLaw", source = "claim.submission.areaOfLaw")
  @Mapping(target = "officeAccountNumber", source = "claim.submission.officeAccountNumber")
  @Mapping(target = "submissionPeriod", source = "claim.submission.submissionPeriod")
  @Mapping(target = "feeCode", source = "claim.feeCode")
  @Mapping(target = "schemeId", source = "claim.schemeId")
  @Mapping(target = "netProfitCostsAmount", source = "summaryFee.netProfitCostsAmount")
  @Mapping(target = "netWaitingCostsAmount", source = "summaryFee.netWaitingCostsAmount")
  @Mapping(target = "jrFormFillingAmount", source = "summaryFee.jrFormFillingAmount")
  @Mapping(target = "isVatApplicable", source = "summaryFee.isVatApplicable")
  @Mapping(target = "categoryOfLaw", source = "calculatedFeeDetail.categoryOfLaw")
  @Mapping(target = "calculatedFeeDetail", source = "calculatedFeeDetail")
  @Mapping(target = "latestAssessment", source = "latestAssessment")
  ClaimStateSnapshot toSnapshot(
      Claim claim,
      Client client,
      ClaimCase claimCase,
      ClaimSummaryFee summaryFee,
      CalculatedFeeDetail calculatedFeeDetail,
      Assessment latestAssessment);

  /**
   * Convenience overload accepting {@link Optional} associations.
   *
   * @param claim the claim entity (required)
   * @param client the associated client, if any
   * @param claimCase the associated claim case, if any
   * @param summaryFee the associated claim summary fee, if any
   * @param calculatedFeeDetail the latest calculated fee detail, if any
   * @param latestAssessment the latest assessment, if any
   * @return an immutable snapshot of the current stored claim values
   */
  default ClaimStateSnapshot toSnapshot(
      Claim claim,
      Optional<Client> client,
      Optional<ClaimCase> claimCase,
      Optional<ClaimSummaryFee> summaryFee,
      Optional<CalculatedFeeDetail> calculatedFeeDetail,
      Optional<Assessment> latestAssessment) {

    return toSnapshot(
        claim,
        client.orElse(null),
        claimCase.orElse(null),
        summaryFee.orElse(null),
        calculatedFeeDetail.orElse(null),
        latestAssessment.orElse(null));
  }

  /** Map the FeeCalculationResponse to a CalculatedFeeDetailSnapshot. */
  @Mapping(target = "totalAmount", source = "feeCalculation.totalAmount")
  @Mapping(target = "vatIndicator", source = "feeCalculation.vatIndicator")
  @Mapping(target = "vatRateApplied", source = "feeCalculation.vatRateApplied")
  @Mapping(target = "calculatedVatAmount", source = "feeCalculation.calculatedVatAmount")
  @Mapping(target = "disbursementAmount", source = "feeCalculation.disbursementAmount")
  @Mapping(
      target = "requestedNetDisbursementAmount",
      source = "feeCalculation.requestedNetDisbursementAmount")
  @Mapping(target = "disbursementVatAmount", source = "feeCalculation.disbursementVatAmount")
  @Mapping(target = "hourlyTotalAmount", source = "feeCalculation.hourlyTotalAmount")
  @Mapping(target = "fixedFeeAmount", source = "feeCalculation.fixedFeeAmount")
  @Mapping(target = "netProfitCostsAmount", source = "feeCalculation.netProfitCostsAmount")
  @Mapping(
      target = "requestedNetProfitCostsAmount",
      source = "feeCalculation.requestedNetProfitCostsAmount")
  @Mapping(target = "netCostOfCounselAmount", source = "feeCalculation.netCostOfCounselAmount")
  @Mapping(target = "netTravelCostsAmount", source = "feeCalculation.netTravelCostsAmount")
  @Mapping(target = "netWaitingCostsAmount", source = "feeCalculation.netWaitingCostsAmount")
  @Mapping(
      target = "detentionTravelAndWaitingCostsAmount",
      source = "feeCalculation.detentionTravelAndWaitingCostsAmount")
  @Mapping(target = "jrFormFillingAmount", source = "feeCalculation.jrFormFillingAmount")
  @Mapping(
      target = "travelAndWaitingCostsAmount",
      source = "feeCalculation.travelAndWaitingCostAmount")
  // Bolt-on fields nested under feeCalculation -> boltOnFeeDetails
  @Mapping(
      target = "boltOnTotalFeeAmount",
      source = "feeCalculation.boltOnFeeDetails.boltOnTotalFeeAmount")
  @Mapping(
      target = "boltOnAdjournedHearingCount",
      source = "feeCalculation.boltOnFeeDetails.boltOnAdjournedHearingCount")
  @Mapping(
      target = "boltOnAdjournedHearingFee",
      source = "feeCalculation.boltOnFeeDetails.boltOnAdjournedHearingFee")
  @Mapping(
      target = "boltOnCmrhTelephoneCount",
      source = "feeCalculation.boltOnFeeDetails.boltOnCmrhTelephoneCount")
  @Mapping(
      target = "boltOnCmrhTelephoneFee",
      source = "feeCalculation.boltOnFeeDetails.boltOnCmrhTelephoneFee")
  @Mapping(
      target = "boltOnCmrhOralCount",
      source = "feeCalculation.boltOnFeeDetails.boltOnCmrhOralCount")
  @Mapping(
      target = "boltOnCmrhOralFee",
      source = "feeCalculation.boltOnFeeDetails.boltOnCmrhOralFee")
  @Mapping(
      target = "boltOnHomeOfficeInterviewCount",
      source = "feeCalculation.boltOnFeeDetails.boltOnHomeOfficeInterviewCount")
  @Mapping(
      target = "boltOnHomeOfficeInterviewFee",
      source = "feeCalculation.boltOnFeeDetails.boltOnHomeOfficeInterviewFee")
  @Mapping(
      target = "boltOnSubstantiveHearingFee",
      source = "feeCalculation.boltOnFeeDetails.boltOnSubstantiveHearingFee")
  CalculatedFeeDetailSnapshot toSnapshot(FeeCalculationResponse response);

  /**
   * Maps the latest calculated fee detail to its read-only snapshot. Same-named fields auto-map.
   *
   * @param calculatedFeeDetail the calculated fee detail entity, may be {@code null}
   * @return the snapshot, or {@code null} if the input is {@code null}
   */
  CalculatedFeeDetailSnapshot toCalculatedFeeDetailSnapshot(
      CalculatedFeeDetail calculatedFeeDetail);

  /**
   * Maps the latest assessment to its read-only snapshot. Same-named fields auto-map.
   *
   * @param assessment the assessment entity, may be {@code null}
   * @return the snapshot, or {@code null} if the input is {@code null}
   */
  AssessmentSnapshot toAssessmentSnapshot(Assessment assessment);
}
