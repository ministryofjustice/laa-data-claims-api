package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationErrorLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetValidationErrors200Response;

@Mapper(componentModel = "spring", uses = SubmissionMapper.class)
public interface ValidationErrorMapper {

  GetValidationErrors200Response toGetValidationErrors200Response(
      Page<ValidationErrorLog> validationErrors);
}
