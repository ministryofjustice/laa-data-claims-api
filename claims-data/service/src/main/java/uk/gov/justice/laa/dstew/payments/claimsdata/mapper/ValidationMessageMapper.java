package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagesResponse;

/** Maps {@link ValidationMessageLog} entities to API response models. */
@Mapper(componentModel = "spring", uses = SubmissionMapper.class)
public interface ValidationMessageMapper {

  /**
   * Converts a page of {@link ValidationMessageLog} entities to a response model.
   *
   * @param validationMessages the paginated validation messages to convert
   * @return the API response containing validation message details
   */
  @Mapping(target = "totalClaims", ignore = true)
  ValidationMessagesResponse toValidationMessagesResponse(
      Page<ValidationMessageLog> validationMessages);
}
