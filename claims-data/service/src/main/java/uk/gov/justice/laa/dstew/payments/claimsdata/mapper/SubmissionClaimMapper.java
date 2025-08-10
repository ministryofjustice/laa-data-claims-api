package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.SubmissionClaim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetSubmission200ResponseClaimsInner;

/**
 * MapStruct mapper for converting between claim models and entities.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SubmissionClaimMapper {

  /** Map a {@link ClaimPost} to a {@link SubmissionClaim} entity. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "submission", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  @Mapping(target = "dutySolicitor", source = "isDutySolicitor")
  @Mapping(target = "youthCourt", source = "isYouthCourt")
  SubmissionClaim toSubmissionClaim(ClaimPost claimPost);

  /** Map a {@link SubmissionClaim} entity to {@link ClaimFields}. */
  @Mapping(target = "isDutySolicitor", source = "dutySolicitor")
  @Mapping(target = "isYouthCourt", source = "youthCourt")
  ClaimFields toClaimFields(SubmissionClaim entity);

  /** Map a {@link SubmissionClaim} to summary response model. */
  @Mapping(target = "claimId", source = "id")
  GetSubmission200ResponseClaimsInner toGetSubmission200ResponseClaimsInner(SubmissionClaim entity);

  /** Update an existing {@link SubmissionClaim} from a {@link ClaimPatch}. */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "submission", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  @Mapping(target = "dutySolicitor", source = "isDutySolicitor")
  @Mapping(target = "youthCourt", source = "isYouthCourt")
  void updateSubmissionClaimFromPatch(ClaimPatch patch, @MappingTarget SubmissionClaim entity);
}

