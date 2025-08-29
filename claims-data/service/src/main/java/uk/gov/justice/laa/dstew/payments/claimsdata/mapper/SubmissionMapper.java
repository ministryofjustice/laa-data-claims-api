package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationErrorLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPost;

/** MapStruct mapper for converting between API models and Submission entities. */
@Mapper(
    componentModel = "spring",
    uses = GlobalStringMapper.class,
    imports = {java.util.UUID.class})
public interface SubmissionMapper {
  /**
   * Map a {@link SubmissionPost} to a {@link Submission} entity.
   *
   * @param submissionPost the request model
   * @return mapped {@link Submission} entity
   */
  @Mapping(target = "id", source = "submissionId")
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  @Mapping(target = "errorMessages", ignore = true)
  Submission toSubmission(SubmissionPost submissionPost);

  /**
   * Map a {@link Submission} entity to {@link SubmissionFields} view model.
   *
   * @param submission the entity
   * @return mapped {@link SubmissionFields}
   */
  @Mapping(target = "submissionId", source = "id")
  @Mapping(target = "submitted", source = "createdOn")
  SubmissionFields toSubmissionFields(Submission submission);

  /**
   * Converts an instant to a LocalDate.
   *
   * @param instant the instant to convert
   * @return the converted LocalDate
   */
  default LocalDate mapInstantToLocalDate(Instant instant) {
    return instant != null ? instant.atZone(ZoneId.systemDefault()).toLocalDate() : null;
  }

  /**
   * Update a {@link Submission} entity from a {@link SubmissionPatch}. Only non-null values from
   * the patch will be copied.
   *
   * @param patch the patch object
   * @param entity the entity to update
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  @Mapping(target = "errorMessages", ignore = true)
  void updateSubmissionFromPatch(SubmissionPatch patch, @MappingTarget Submission entity);

  /** Map a validation error string to a ValidationErrorLog. */
  @Mapping(target = "id", expression = "java(UUID.randomUUID())")
  @Mapping(target = "submission", source = "submission")
  @Mapping(target = "claim", ignore = true)
  @Mapping(target = "errorCode", source = "error")
  @Mapping(target = "errorDescription", source = "error")
  @Mapping(target = "createdByUserId", constant = "todo")
  ValidationErrorLog toValidationErrorLog(String error, Submission submission);
}
