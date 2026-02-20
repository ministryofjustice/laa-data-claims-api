package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BoltOnPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponseV2;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionClaim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;

/** MapStruct mapper for converting between claim models and entities. */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    uses = GlobalStringMapper.class,
    imports = {com.fasterxml.uuid.Generators.class},
    config = AuditFieldsMapper.class)
public interface ClaimMapper {

  /** Map a {@link ClaimPost} to a {@link Claim} entity. */
  @InheritConfiguration(name = "ignoreAuditFieldsAndId")
  @Mapping(target = "submission", ignore = true)
  @Mapping(target = "dutySolicitor", source = "isDutySolicitor")
  @Mapping(target = "youthCourt", source = "isYouthCourt")
  Claim toClaim(ClaimPost claimPost);

  /**
   * Map a {@link Claim} entity to {@link
   * uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse}.
   */
  @Mapping(target = "isDutySolicitor", source = "dutySolicitor")
  @Mapping(target = "isYouthCourt", source = "youthCourt")
  @Mapping(target = "submissionId", source = "submission.id")
  @Mapping(target = "submissionPeriod", source = "submission.submissionPeriod")
  ClaimResponse toClaimResponse(Claim entity);

  @Mapping(target = "isDutySolicitor", source = "dutySolicitor")
  @Mapping(target = "isYouthCourt", source = "youthCourt")
  @Mapping(target = "submissionId", source = "submission.id")
  @Mapping(target = "submissionPeriod", source = "submission.submissionPeriod")
  @Mapping(target = "areaOfLaw", source = "submission.areaOfLaw")
  @Mapping(target = "officeCode", source = "submission.officeAccountNumber")
  @Mapping(target = "id", source = "id")
  @Mapping(target = "createdByUserId", source = "createdByUserId")
  @Mapping(
      target = "netProfitCostsAmount",
      source = "claimSummaryFee",
      qualifiedByName = "firstNetProfitCostsAmount")
  @Mapping(
      target = "netWaitingCostsAmount",
      source = "claimSummaryFee",
      qualifiedByName = "firstNetWaitingCostsAmount")
  @Mapping(
      target = "jrFormFillingAmount",
      source = "claimSummaryFee",
      qualifiedByName = "firstJrFormFillingAmount")
  @Mapping(
      target = "disbursementsVatAmount",
      source = "claimSummaryFee",
      qualifiedByName = "firstDisbursementsVatAmount")
  @Mapping(
      target = "isVatApplicable",
      source = "claimSummaryFee",
      qualifiedByName = "firstIsVatApplicable")
  @Mapping(
      target = "netDisbursementAmount",
      source = "claimSummaryFee",
      qualifiedByName = "firstNetDisbursementAmount")
  @Mapping(
      target = "travelWaitingCostsAmount",
      source = "claimSummaryFee",
      qualifiedByName = "firstTravelWaitingCostsAmount")
  @Mapping(target = ".", source = "client")
  @Mapping(target = ".", source = "claimCase")
  @Mapping(
      target = "feeCalculationResponse",
      source = "calculatedFeeDetail",
      qualifiedByName = "mapFeeCalculationResponseFromCalculatedFeeDetail")
  @Mapping(target = ".", source = "claimSummaryFee")
  ClaimResponseV2 toClaimResponseV2(Claim entity);

  /** Null safety check for first ClaimSummaryFee. */
  @Named("firstNetProfitCostsAmount")
  default BigDecimal firstNetProfitCostsAmount(List<ClaimSummaryFee> list) {
    if (list == null || list.isEmpty() || list.getFirst() == null) {
      return null;
    }
    return list.getFirst().getNetProfitCostsAmount();
  }

  /** Null safety check for first ClaimSummaryFee. */
  @Named("firstNetWaitingCostsAmount")
  default BigDecimal firstNetWaitingCostsAmount(List<ClaimSummaryFee> list) {
    if (list == null || list.isEmpty() || list.getFirst() == null) {
      return null;
    }
    return list.getFirst().getNetWaitingCostsAmount();
  }

  /** Null safety check for first ClaimSummaryFee. */
  @Named("firstJrFormFillingAmount")
  default BigDecimal firstJrFormFillingAmount(List<ClaimSummaryFee> list) {
    if (list == null || list.isEmpty() || list.getFirst() == null) {
      return null;
    }
    return list.getFirst().getJrFormFillingAmount();
  }

  /** Null safety check for first ClaimSummaryFee. */
  @Named("firstDisbursementsVatAmount")
  default BigDecimal firstDisbursementsVatAmount(List<ClaimSummaryFee> list) {
    if (list == null || list.isEmpty() || list.getFirst() == null) {
      return null;
    }
    return list.getFirst().getDisbursementsVatAmount();
  }

  /** Null safety check for first ClaimSummaryFee. */
  @Named("firstIsVatApplicable")
  default Boolean firstIsVatApplicable(List<ClaimSummaryFee> list) {
    if (list == null || list.isEmpty() || list.getFirst() == null) {
      return null;
    }
    return list.getFirst().getIsVatApplicable();
  }

  /** Null safety check for first ClaimSummaryFee. */
  @Named("firstNetDisbursementAmount")
  default BigDecimal firstNetDisbursementAmount(List<ClaimSummaryFee> list) {
    if (list == null || list.isEmpty() || list.getFirst() == null) {
      return null;
    }
    return list.getFirst().getNetDisbursementAmount();
  }

  /** Null safety check for first ClaimSummaryFee. */
  @Named("firstTravelWaitingCostsAmount")
  default BigDecimal firstTravelWaitingCostsAmount(List<ClaimSummaryFee> list) {
    if (list == null || list.isEmpty() || list.getFirst() == null) {
      return null;
    }
    return list.getFirst().getTravelWaitingCostsAmount();
  }

  /**
   * Map a {@link uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionClaim} to summary
   * response model.
   */
  @Mapping(target = "claimId", source = "id")
  SubmissionClaim toSubmissionClaim(Claim entity);

  /** Update an existing {@link Claim} from a {@link ClaimPatch}. */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @InheritConfiguration(name = "ignoreAuditFieldsAndId")
  @Mapping(target = "submission", ignore = true)
  @Mapping(target = "dutySolicitor", source = "isDutySolicitor")
  @Mapping(target = "youthCourt", source = "isYouthCourt")
  void updateSubmissionClaimFromPatch(ClaimPatch patch, @MappingTarget Claim entity);

  /** Map a validation error string to a ValidationErrorLog. */
  @Mapping(target = "id", expression = "java(Generators.timeBasedEpochGenerator().generate())")
  @Mapping(target = "submissionId", source = "claim.submission.id")
  @Mapping(target = "claimId", source = "claim.id")
  @Mapping(target = "displayMessage", source = "message.displayMessage")
  @Mapping(target = "technicalMessage", source = "message.technicalMessage")
  @Mapping(target = "type", source = "message.type")
  @Mapping(target = "source", source = "message.source")
  ValidationMessageLog toValidationMessageLog(ValidationMessagePatch message, Claim claim);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @InheritConfiguration(name = "ignoreAuditFieldsAndId")
  @Mapping(target = "claim", ignore = true)
  ClaimSummaryFee toClaimSummaryFee(ClaimPost claimPost);

  /** Map a fee calculation response string to a calculated fee detail. */
  @Mapping(target = "id", expression = "java(Generators.timeBasedEpochGenerator().generate())")
  @Mapping(target = "claimSummaryFee", ignore = true)
  @Mapping(target = "claim", ignore = true)
  @InheritConfiguration(name = "ignoreAuditFields")
  @Mapping(target = "feeCode", source = "response.feeCode")
  @Mapping(target = "boltOnTotalFeeAmount", source = "response.boltOnDetails.boltOnTotalFeeAmount")
  @Mapping(
      target = "boltOnAdjournedHearingCount",
      source = "response.boltOnDetails.boltOnAdjournedHearingCount")
  @Mapping(
      target = "boltOnAdjournedHearingFee",
      source = "response.boltOnDetails.boltOnAdjournedHearingFee")
  @Mapping(
      target = "boltOnCmrhTelephoneCount",
      source = "response.boltOnDetails.boltOnCmrhTelephoneCount")
  @Mapping(
      target = "boltOnCmrhTelephoneFee",
      source = "response.boltOnDetails.boltOnCmrhTelephoneFee")
  @Mapping(target = "boltOnCmrhOralCount", source = "response.boltOnDetails.boltOnCmrhOralCount")
  @Mapping(target = "boltOnCmrhOralFee", source = "response.boltOnDetails.boltOnCmrhOralFee")
  @Mapping(
      target = "boltOnHomeOfficeInterviewCount",
      source = "response.boltOnDetails.boltOnHomeOfficeInterviewCount")
  @Mapping(
      target = "boltOnHomeOfficeInterviewFee",
      source = "response.boltOnDetails.boltOnHomeOfficeInterviewFee")
  @Mapping(
      target = "boltOnSubstantiveHearingFee",
      source = "response.boltOnDetails.boltOnSubstantiveHearingFee")
  @Mapping(target = "escapeCaseFlag", source = "response.boltOnDetails.escapeCaseFlag")
  @Mapping(target = "schemeId", source = "response.boltOnDetails.schemeId")
  CalculatedFeeDetail toCalculatedFeeDetail(FeeCalculationPatch response);

  @Mapping(target = "id", ignore = true)
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void updateClaimResponseFromClaimSummaryFee(
      ClaimSummaryFee entity, @MappingTarget ClaimResponse claim);

  @Mapping(
      target = "feeCalculationResponse",
      source = "entity",
      qualifiedByName = "updateFeeCalculationResponseFromCalculatedFeeDetail")
  @BeanMapping(
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
      ignoreByDefault = true)
  void updateClaimResponseFromCalculatedFeeDetail(
      CalculatedFeeDetail entity, @MappingTarget ClaimResponse claim);

  @Named("updateFeeCalculationResponseFromCalculatedFeeDetail")
  @Mapping(target = "claimId", source = "claim.id")
  @Mapping(target = "claimSummaryFeeId", source = "claimSummaryFee.id")
  @Mapping(target = "calculatedFeeDetailId", source = "id")
  @Mapping(
      target = "boltOnDetails",
      source = "entity",
      qualifiedByName = "updateBoltOnDetailsFromCalculatedFeeDetail")
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void updateFeeCalculationResponseFromCalculatedFeeDetail(
      CalculatedFeeDetail entity, @MappingTarget FeeCalculationPatch feeCalculationResponse);

  @Named("updateBoltOnDetailsFromCalculatedFeeDetail")
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void updateBoltOnDetailsFromCalculatedFeeDetail(
      CalculatedFeeDetail entity, @MappingTarget BoltOnPatch boltOnDetails);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @InheritConfiguration(name = "ignoreAuditFieldsAndId")
  @Mapping(target = "claim", ignore = true)
  ClaimCase toClaimCase(ClaimPost claimPost);

  @Mapping(target = "id", ignore = true)
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void updateClaimResponseFromClaimCase(ClaimCase entity, @MappingTarget ClaimResponse claim);

  @Mapping(target = "totalWarnings", source = "totalWarningMessages")
  void updateTotalWarningMessages(Long totalWarningMessages, @MappingTarget ClaimResponse claim);

  @Mapping(target = "totalWarnings", source = "totalWarningMessages")
  void updateTotalWarningMessagesV2(
      Long totalWarningMessages, @MappingTarget ClaimResponseV2 claim);

  /**
   * Map a {@link CalculatedFeeDetail} entity to {@link
   * uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationPatch}.
   */
  @Named("mapFeeCalculationResponseFromCalculatedFeeDetail")
  @Mapping(target = "claimId", source = "claim.id")
  @Mapping(target = "claimSummaryFeeId", source = "claimSummaryFee.id")
  @Mapping(target = "calculatedFeeDetailId", source = "id")
  @Mapping(target = "boltOnDetails", source = "entity")
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  default FeeCalculationPatch mapFeeCalculationResponseFromCalculatedFeeDetail(
      CalculatedFeeDetail entity) {

    if (entity == null) {
      return null;
    }
    FeeCalculationPatch target = new FeeCalculationPatch();
    // reuse your existing update method to avoid duplicating mapping config:
    updateFeeCalculationResponseFromCalculatedFeeDetail(entity, target);
    return target;
  }
}
