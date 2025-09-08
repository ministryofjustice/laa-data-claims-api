package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionsResultSet;

/**
 * Mapper interface for transforming a Page object of {@link Submission} entities to a {@link
 * SubmissionsResultSet}.
 *
 * <p>It leverages the MapStruct library, with the Spring component model and {@link
 * SubmissionMapper}, for automatic generation of {@link SubmissionFields} to be added as a content
 * of the {@code SubmissionsResultSet}.
 */
@Mapper(componentModel = "spring", uses = SubmissionMapper.class)
public interface SubmissionsResultSetMapper {

  SubmissionsResultSet toSubmissionsResultSet(Page<Submission> submissions);
}
