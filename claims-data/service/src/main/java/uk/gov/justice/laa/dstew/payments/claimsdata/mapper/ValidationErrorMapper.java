package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationErrorLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetValidationErrors200Response;

/** Maps {@link ValidationErrorLog} entities to API response models. */
@Mapper(componentModel = "spring", uses = SubmissionMapper.class)
public interface ValidationErrorMapper {

  /**
   * Converts a page of {@link ValidationErrorLog} entities to a response model.
   *
   * @param validationErrors the paginated validation errors to convert
   * @return the API response containing validation error details
   */
  GetValidationErrors200Response toGetValidationErrors200Response(
      Page<ValidationErrorLog> validationErrors);
}
