package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.mapstruct.BeanMapping;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionBase;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.projection.SubmissionProjection;

/** MapStruct mapper for converting between API models and Submission entities. */
@Mapper(
    componentModel = "spring",
    uses = {GlobalStringMapper.class, GlobalDateTimeMapper.class},
    imports = {com.fasterxml.uuid.Generators.class},
    config = AuditFieldsMapper.class)
public interface SubmissionMapper {
  /**
   * Map a {@link SubmissionPost} to a {@link Submission} entity.
   *
   * @param submissionPost the request model
   * @return mapped {@link Submission} entity
   */
  @Mapping(target = "id", source = "submissionId")
  @InheritConfiguration(name = "ignoreAuditFields")
  @Mapping(target = "errorMessages", ignore = true)
  @Mapping(target = "createdOn", source = "submitted")
  Submission toSubmission(SubmissionPost submissionPost);

  /**
   * Map a {@link Submission} entity to {@link SubmissionResponse} view model.
   *
   * @param submission the entity
   * @return mapped {@link SubmissionResponse}
   */
  @Mapping(target = "submissionId", source = "id")
  @Mapping(target = "submitted", source = "createdOn")
  SubmissionBase toSubmissionBase(Submission submission);

  SubmissionResponse toSubmissionResponse(SubmissionProjection projection);

  /**
   * Converts an Instant to a String.
   *
   * @param instant the instant to convert
   * @return the formatted String
   */
  default String mapInstantToString(Instant instant) {
    return instant != null
        ? DateTimeFormatter.ofPattern("dd/MM/yyyy").format(instant.atZone(ZoneId.systemDefault()))
        : null;
  }

  /**
   * Update a {@link Submission} entity from a {@link SubmissionPatch}. Only non-null values from
   * the patch will be copied.
   *
   * @param patch the patch object
   * @param entity the entity to update
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @InheritConfiguration(name = "ignoreAuditFieldsAndId")
  @Mapping(target = "errorMessages", ignore = true)
  void updateSubmissionFromPatch(SubmissionPatch patch, @MappingTarget Submission entity);

  /** Map a validation message string to a ValidationMessageLog. */
  @Mapping(target = "id", expression = "java(Generators.timeBasedEpochGenerator().generate())")
  @Mapping(target = "submissionId", source = "submission.id")
  @Mapping(target = "claimId", ignore = true)
  @Mapping(target = "displayMessage", source = "message.displayMessage")
  @Mapping(target = "technicalMessage", source = "message.technicalMessage")
  @Mapping(target = "type", source = "message.type")
  @Mapping(target = "source", source = "message.source")
  ValidationMessageLog toValidationMessageLog(
      ValidationMessagePatch message, Submission submission);
}
