package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentDiff;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimAmendmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Writes the durable business record of a successful claim amendment (DSTEW-1907).
 *
 * <p>For a successful amendment it:
 *
 * <ol>
 *   <li>assembles and inserts exactly one {@code claim_amendment} row (UUIDv7 id, metadata, {@code
 *       before_state}, {@code request_payload} and the versioned {@code diff});
 *   <li>applies the amended {@code claim}-table values and {@code is_amended = true} onto the
 *       managed {@link Claim} entity - these column writes are folded into the guarded claim update
 *       owned by DSTEW-1753, so this service never issues its own claim save;
 *   <li>attaches the single amendment-driven {@code calculated_fee_detail} row, when present, via
 *       {@link AmendmentCalculatedFeeWriter} (DSTEW-1762).
 * </ol>
 *
 * <p><b>Transaction:</b> this service does not open, commit or roll back a transaction. It must be
 * invoked within the single atomic amendment transaction owned by DSTEW-1771, so that every write -
 * the {@code claim_amendment} insert, the guarded claim update and the calculated-fee insert -
 * commits together or not at all. It must only be called on a successful amendment (validation
 * collected no errors and the final version guard passes).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClaimAmendmentPersistenceService {

  private final ClaimAmendmentRepository claimAmendmentRepository;
  private final AmendmentDiffAssembler diffAssembler;
  private final AmendmentJsonWriter jsonWriter;
  private final AmendmentEntitiesWriter entitiesWriter;
  private final AmendmentCalculatedFeeWriter calculatedFeeWriter;

  /**
   * Persists the business record for a successful amendment of the supplied managed claim.
   *
   * @param claim the managed claim being amended (mutated in place; not saved here)
   * @param state the in-memory amendment state (before/after snapshots and the request payload)
   * @return the inserted {@code claim_amendment} row
   */
  public ClaimAmendment persistSuccessfulAmendment(Claim claim, ClaimAmendmentState state) {
    ClaimAmendmentPayload payload = state.getRequestPayload();
    AmendmentDiff diff = diffAssembler.assemble(state);

    ClaimAmendment amendment =
        ClaimAmendment.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claim)
            .requestedByCode(unwrap(payload.getAmendmentRequestedBy()))
            .amendmentReasonCode(unwrap(payload.getAmendmentReasonCode()))
            .createdByUserId(unwrap(payload.getAmendmentUserId()))
            .createdOn(OffsetDateTime.now(ZoneOffset.UTC))
            .beforeState(jsonWriter.writeBeforeState(state.getBeforeState()))
            .requestPayload(jsonWriter.writeRequestPayload(payload))
            .diff(jsonWriter.writeDiff(diff))
            .build();

    ClaimAmendment savedAmendment = claimAmendmentRepository.save(amendment);
    log.debug("Inserted claim_amendment {} for claim {}", savedAmendment.getId(), claim.getId());

    // Contribute the amended column writes to the managed claim and its related entities (client,
    // claim_case, claim_summary_fee). The version-guarded claim update and the version increment
    // are
    // owned by DSTEW-1753; we never issue our own save here.
    entitiesWriter.applyAmendedValues(claim, state.getPostAmendmentState());

    // Attach the amendment-driven calculated_fee_detail row (one per pricing amendment) from the
    // FSP handoff; a non-pricing amendment attaches nothing.
    calculatedFeeWriter.attach(savedAmendment, state);

    return savedAmendment;
  }

  private static String unwrap(JsonNullable<String> value) {
    return value != null && value.isPresent() ? value.get() : null;
  }
}
