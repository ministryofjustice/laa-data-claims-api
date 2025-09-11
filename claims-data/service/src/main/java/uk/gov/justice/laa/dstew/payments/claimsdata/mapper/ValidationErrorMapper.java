package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationErrorLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationErrorsResponse;

/** Maps {@link ValidationErrorLog} entities to API response models. */
@Mapper(componentModel = "spring", uses = SubmissionMapper.class)
public interface ValidationErrorMapper {

  /**
   * Converts a page of {@link ValidationErrorLog} entities to a response model.
   *
   * @param validationErrors the paginated validation errors to convert
   * @return the API response containing validation error details
   */
  @Mapping(target = "totalClaims", ignore = true)
  ValidationErrorsResponse toValidationErrorsResponse(Page<ValidationErrorLog> validationErrors);
}
