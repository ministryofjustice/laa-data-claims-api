package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionClaim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;

/** MapStruct mapper for converting between claim models and entities. */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    uses = GlobalStringMapper.class,
    imports = {com.fasterxml.uuid.Generators.class})
public interface ClaimMapper {

  // TODO: DSTEW-323 isolate common @Mapping annotations in one place (6 methods are currently using
  // these)
  /** Map a {@link ClaimPost} to a {@link Claim} entity. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "submission", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  @Mapping(target = "dutySolicitor", source = "isDutySolicitor")
  @Mapping(target = "youthCourt", source = "isYouthCourt")
  Claim toClaim(ClaimPost claimPost);

  /**
   * Map a {@link Claim} entity to {@link
   * uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse}.
   */
  @Mapping(target = "isDutySolicitor", source = "dutySolicitor")
  @Mapping(target = "isYouthCourt", source = "youthCourt")
  ClaimResponse toClaimResponse(Claim entity);

  /**
   * Map a {@link uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionClaim} to summary
   * response model.
   */
  @Mapping(target = "claimId", source = "id")
  SubmissionClaim toSubmissionClaim(Claim entity);

  /** Update an existing {@link Claim} from a {@link ClaimPatch}. */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "submission", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
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
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "claim", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  ClaimSummaryFee toClaimSummaryFee(ClaimPost claimPost);

  /** Update an existing {@link ClaimSummaryFee} from a {@link ClaimPatch}. */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  void updateClaimSummaryFeeFromPatch(
      ClaimPatch claimPatch, @MappingTarget ClaimSummaryFee claimSummaryFee);

  /** Map a fee calculation response string to a calculated fee detail. */
  @Mapping(target = "id", expression = "java(Generators.timeBasedEpochGenerator().generate())")
  @Mapping(target = "claimSummaryFee", ignore = true)
  @Mapping(target = "claim", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
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
  @Mapping(target = "escapeCaseFlag", source = "response.boltOnDetails.escapeCaseFlag")
  @Mapping(target = "schemeId", source = "response.boltOnDetails.schemeId")
  CalculatedFeeDetail toCalculatedFeeDetail(FeeCalculationPatch response);
}
