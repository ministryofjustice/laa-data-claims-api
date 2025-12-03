package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;

/** MapStruct mapper for converting between assessment models and entities. */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    uses = {GlobalStringMapper.class, GlobalDateTimeMapper.class},
    imports = {com.fasterxml.uuid.Generators.class},
    config = AuditFieldsMapper.class)
public interface AssessmentMapper {

  /** Map an {@link AssessmentPost} to an {@link Assessment} entity. */
  @InheritConfiguration(name = "ignoreAuditFieldsAndId")
  @Mapping(target = "claim", ignore = true)
  @Mapping(target = "claimSummaryFee", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  Assessment toAssessment(AssessmentPost assessmentPost);

  /** Map a validation error string to a ValidationErrorLog. */
  @Mapping(target = "id", expression = "java(Generators.timeBasedEpochGenerator().generate())")
  @Mapping(target = "submissionId", source = "assessment.claim.submission.id")
  @Mapping(target = "claimId", source = "assessment.claim.id")
  @Mapping(target = "displayMessage", source = "message.displayMessage")
  @Mapping(target = "technicalMessage", source = "message.technicalMessage")
  @Mapping(target = "type", source = "message.type")
  @Mapping(target = "source", source = "message.source")
  ValidationMessageLog toValidationMessageLog(
      ValidationMessagePatch message, Assessment assessment);

  @Mapping(target = "claimId", source = "claim.id")
  @Mapping(target = "claimSummaryFeeId", source = "claimSummaryFee.id")
  AssessmentGet toAssessmentGet(Assessment assessment);
}
