package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSet;

/**
 * Mapper interface for transforming a Page object of {@link Claim} entities to a {@link
 * ClaimResultSet}.
 *
 * <p>It leverages the MapStruct library, with the Spring component model and {@link
 * SubmissionMapper}, for automatic generation of {@link Claim} to be added as a content of the
 * {@code ClaimResultSet}.
 */
@Mapper(componentModel = "spring", uses = ClaimMapper.class)
public interface ClaimResultSetMapper {
  ClaimResultSet toClaimResultSet(Page<Claim> claims);
}
