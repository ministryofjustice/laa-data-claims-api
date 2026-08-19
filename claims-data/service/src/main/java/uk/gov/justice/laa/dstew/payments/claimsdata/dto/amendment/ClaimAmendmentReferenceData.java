package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import java.util.List;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReferenceEntity;

/**
 * Immutable snapshot of the governed amendment reference data: every Requested By value and every
 * Amendment Reason value (active and inactive), each in display order.
 *
 * <p>This is the cacheable unit shared by the read endpoint and the amendment validation step. The
 * raw lists are cached (not a pre-indexed view) because consumers need different indexings: the
 * read endpoint groups reasons by Requested By to nest them, whereas validation indexes by code.
 *
 * @param requestedBy all Requested By reference values in display order
 * @param reasons all Amendment Reason reference values in display order
 */
public record ClaimAmendmentReferenceData(
    List<RequestedByReferenceEntity> requestedBy, List<AmendmentReasonReferenceEntity> reasons) {

  /**
   * Indicates whether the reference data is incomplete (either list empty), which a consumer may
   * treat as the data being unavailable.
   *
   * @return {@code true} if either reference list is empty
   */
  public boolean isEmpty() {
    return requestedBy.isEmpty() || reasons.isEmpty();
  }
}
