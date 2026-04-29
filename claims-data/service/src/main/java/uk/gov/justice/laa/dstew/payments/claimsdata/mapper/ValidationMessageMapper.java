package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagesResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ValidationMessageWithClaimDetailsProjection;

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
  @Mapping(target = "content", ignore = true)
  ValidationMessagesResponse toValidationMessagesResponse(
      Page<ValidationMessageLog> validationMessages);

  /**
   * Converts a page of {@link ValidationMessageWithClaimDetailsProjection} to a response model.
   *
   * @param validationMessages the paginated projections to convert
   * @return the API response containing validation message and claim details
   */
  @Mapping(target = "totalClaims", ignore = true)
  ValidationMessagesResponse toValidationMessagesResponseFromProjection(
      Page<ValidationMessageWithClaimDetailsProjection> validationMessages);
}
