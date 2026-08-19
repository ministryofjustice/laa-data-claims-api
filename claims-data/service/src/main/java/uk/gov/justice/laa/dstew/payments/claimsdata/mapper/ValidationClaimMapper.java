package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import java.time.LocalDate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;

/**
 * Maps a fully-resolved {@link ClaimStateSnapshot} (the merged, post-amendment claim state) to the
 * claims-validation-core {@link Claim} model consumed by the shared validation service.
 *
 * <p>Same-named, same-typed properties are mapped automatically by MapStruct. Explicit
 * {@code @Mapping}s cover the handful of naming and typing differences between the two models:
 *
 * <ul>
 *   <li>identity: snapshot {@code claimId} -&gt; claim {@code id};
 *   <li>boolean naming: snapshot {@code amended}/{@code dutySolicitor}/{@code youthCourt} -&gt;
 *       claim {@code isAmended}/{@code isDutySolicitor}/{@code isYouthCourt};
 *   <li>assessment flag: the snapshot exposes a non-standard {@code hasAssessment()} accessor, read
 *       here via an expression.
 * </ul>
 *
 * <p>Date fields are {@link LocalDate} on the snapshot but ISO {@code String}s on the validation
 * model; the {@link #map(LocalDate)} helper is picked up automatically for every such field. The
 * snapshot's read-only context ({@code categoryOfLaw}, calculated fee detail, latest assessment)
 * has no counterpart on {@link Claim} and is ignored. Null-safe: MapStruct null-guards the source.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ValidationClaimMapper {

  /**
   * Converts the merged claim snapshot to the validation-core claim model.
   *
   * @param snapshot the merged (post-amendment) claim state; may be {@code null}
   * @return the mapped validation claim, or {@code null} if the input is {@code null}
   */
  @Mapping(target = "id", source = "claimId")
  @Mapping(target = "isAmended", source = "amended")
  @Mapping(target = "isDutySolicitor", source = "dutySolicitor")
  @Mapping(target = "isYouthCourt", source = "youthCourt")
  @Mapping(target = "hasAssessment", expression = "java(snapshot.hasAssessment())")
  Claim toValidationClaim(ClaimStateSnapshot snapshot);

  /**
   * Renders a {@link LocalDate} as an ISO-8601 date string ({@code yyyy-MM-dd}) for the validation
   * model, which carries dates as strings.
   *
   * @param date the date to render, may be {@code null}
   * @return the ISO date string, or {@code null} if the input is {@code null}
   */
  default String map(LocalDate date) {
    return date != null ? date.toString() : null;
  }
}
