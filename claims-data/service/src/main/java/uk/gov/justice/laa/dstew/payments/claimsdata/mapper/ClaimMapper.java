package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.mapstruct.BeanMapping;
import org.mapstruct.Condition;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.util.StringUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BoltOnPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimAmendmentPatch;
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
    uses = {GlobalStringMapper.class, GlobalDateTimeMapper.class},
    imports = {
      com.fasterxml.uuid.Generators.class,
      uk.gov.justice.laa.dstew.payments.claimsdata.util.DerivedClaimStatusResolver.class
    },
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
  @Mapping(target = "dateSubmitted", source = "submission.createdOn")
  @Mapping(target = "areaOfLaw", source = "submission.areaOfLaw")
  @Mapping(target = "officeCode", source = "submission.officeAccountNumber")
  @Mapping(target = "id", source = "id")
  @Mapping(target = "createdByUserId", source = "createdByUserId")
  // Derived business status - single source of truth is DerivedClaimStatusResolver. This does not
  // replace the raw "status" field, which is mapped automatically and left unchanged.
  @Mapping(
      target = "derivedClaimStatus",
      expression =
          "java(DerivedClaimStatusResolver.resolve(entity.getStatus(), "
              + "entity.isHasAssessment(), entity.isAmended()))")
  // Use the helper method expression to flatten fields from the latest fee's summary
  @Mapping(target = ".", source = "latestCalculatedFee.claimSummaryFee")
  @Mapping(target = ".", source = "client")
  @Mapping(target = ".", source = "claimCase")
  // Extract the specific fee calculation payload from the latest calculated record
  @Mapping(
      target = "feeCalculationResponse",
      source = "latestCalculatedFee",
      qualifiedByName = "mapFeeCalculationResponseFromCalculatedFeeDetail")
  ClaimResponseV2 toClaimResponseV2(Claim entity);

  /**
   * Map a {@link uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionClaim} to summary
   * response model.
   */
  @Mapping(target = "claimId", source = "id")
  SubmissionClaim toSubmissionClaim(Claim entity);

  /**
   * Update an existing {@link Claim} from a {@link ClaimAmendmentPatch}.
   *
   * <p>The patch fields are {@link JsonNullable}: MapStruct (with {@code
   * NullValuePropertyMappingStrategy.IGNORE}) skips omitted fields, applies present values and
   * clears the target when an explicit null is supplied.
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @InheritConfiguration(name = "ignoreAuditFieldsAndId")
  @Mapping(target = "submission", ignore = true)
  @Mapping(target = "dutySolicitor", source = "isDutySolicitor")
  @Mapping(target = "youthCourt", source = "isYouthCourt")
  void updateSubmissionClaimFromPatch(ClaimAmendmentPatch patch, @MappingTarget Claim entity);

  /** Map a validation error string to a ValidationErrorLog. */
  @Mapping(target = "id", expression = "java(Generators.timeBasedEpochGenerator().generate())")
  @Mapping(target = "submissionId", source = "claim.submission.id")
  @Mapping(target = "claimId", source = "claim.id")
  @Mapping(target = "displayMessage", source = "message.displayMessage")
  @Mapping(target = "technicalMessage", source = "message.technicalMessage")
  @Mapping(target = "type", source = "message.type")
  @Mapping(target = "source", source = "message.source")
  @Mapping(target = "messageCode", source = "message.messageCode")
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
    // reuse the existing update method to avoid duplicating mapping config:
    updateFeeCalculationResponseFromCalculatedFeeDetail(entity, target);
    return target;
  }

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  ClaimAmendmentPayload toAmendmentPayload(ClaimAmendmentPatch claimPatch);

  /** Date pattern used across the claim API for {@code String} date fields (e.g. "5/12/2025"). */
  DateTimeFormatter CLAIM_DATE_FORMAT = DateTimeFormatter.ofPattern("d/M/yyyy");

  /**
   * Tri-state converter: {@code JsonNullable<String>} (d/M/yyyy) to {@code
   * JsonNullable<LocalDate>}.
   *
   * <p>Preserves the amendment tri-state: omitted stays undefined, an explicit null stays a present
   * null (a requested clear), and a value is parsed into a {@link LocalDate}.
   */
  default JsonNullable<LocalDate> mapDate(JsonNullable<String> value) {
    if (value == null || !value.isPresent()) {
      return JsonNullable.undefined();
    }
    String raw = value.get();
    return JsonNullable.of(
        StringUtils.hasText(raw) ? LocalDate.parse(raw, CLAIM_DATE_FORMAT) : null);
  }

  /**
   * Tri-state converter: {@code JsonNullable<UUID>} to {@code JsonNullable<String>}, preserving the
   * omitted / explicit-null / value distinction.
   */
  default JsonNullable<String> mapUuid(JsonNullable<UUID> value) {
    if (value == null || !value.isPresent()) {
      return JsonNullable.undefined();
    }
    UUID raw = value.get();
    return JsonNullable.of(raw != null ? raw.toString() : null);
  }

  // ---------------------------------------------------------------------------
  // Unwrapping helpers for JsonNullable -> plain entity fields.
  //
  // The @Condition presence check ensures MapStruct only writes a target field when the source
  // JsonNullable is PRESENT. Combined with the unwrap converters below this yields true PATCH
  // semantics when updating an entity:
  //   * omitted (undefined)     -> condition false -> entity field left unchanged;
  //   * explicit null (of null) -> condition true, unwrap null -> entity field cleared;
  //   * value (of value)        -> condition true, unwrap value -> entity field set.
  // ---------------------------------------------------------------------------

  /** Presence check used by MapStruct to skip omitted (undefined) JsonNullable source fields. */
  @Condition
  default <T> boolean isPresent(JsonNullable<T> value) {
    return value != null && value.isPresent();
  }

  /** Unwrap a present {@code JsonNullable<T>} to its value (which may be null). */
  default <T> T unwrap(JsonNullable<T> value) {
    return value == null ? null : value.orElse(null);
  }

  /** Unwrap a present {@code JsonNullable<String>} (d/M/yyyy) to a {@link LocalDate}. */
  default LocalDate unwrapDate(JsonNullable<String> value) {
    if (value == null || !value.isPresent()) {
      return null;
    }
    String raw = value.get();
    return StringUtils.hasText(raw) ? LocalDate.parse(raw, CLAIM_DATE_FORMAT) : null;
  }
}
