package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ValueMapping;
import org.springframework.data.domain.Page;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimRetentionLookupClaimDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimRetentionLookupResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/** Maps Claim entities to retention lookup response models. */
@Mapper(componentModel = "spring")
public interface ClaimRetentionLookupMapper {

  /**
   * Maps a single Claim entity to a retention lookup claim detail.
   *
   * @param claim the claim entity to map
   * @return the claim retention lookup detail model
   */
  @Mapping(target = "claimId", source = "id")
  ClaimRetentionLookupClaimDetail toClaimRetentionLookupClaimDetail(Claim claim);

  /**
   * Maps the persisted claim status onto the narrow status exposed by this endpoint. The repository
   * query filters to VALID only, so any other value indicates the query and the response contract
   * have drifted apart and must fail loudly rather than be silently reported as VALID.
   *
   * @param status the persisted claim status
   * @return the retention lookup status
   */
  @ValueMapping(source = "VALID", target = "VALID")
  @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
  ClaimRetentionLookupClaimDetail.StatusEnum toRetentionStatus(ClaimStatus status);

  /**
   * Maps a page of Claim entities to a paginated retention lookup result set.
   *
   * @param claimsPage the page of claims to map
   * @return the claim retention lookup result set with pagination metadata
   */
  default ClaimRetentionLookupResultSet toClaimRetentionLookupResultSet(Page<Claim> claimsPage) {
    ClaimRetentionLookupResultSet resultSet = new ClaimRetentionLookupResultSet();

    resultSet.setContent(
        claimsPage.getContent().stream().map(this::toClaimRetentionLookupClaimDetail).toList());

    resultSet.setTotalPages(claimsPage.getTotalPages());
    resultSet.setTotalElements((int) claimsPage.getTotalElements());
    resultSet.setNumber(claimsPage.getNumber());
    resultSet.setSize(claimsPage.getSize());

    return resultSet;
  }

  /**
   * Converts an Instant to OffsetDateTime in UTC.
   *
   * @param instant the instant to convert
   * @return the offset datetime in UTC, or null if input is null
   */
  default OffsetDateTime toOffsetDateTime(Instant instant) {
    return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
