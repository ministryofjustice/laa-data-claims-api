package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentDiff;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;

/**
 * Serialises the amendment JSONB column values ({@code before_state}, {@code request_payload} and
 * {@code diff}) using the application {@link ObjectMapper}.
 *
 * <p>The shared mapper registers {@code JsonNullableModule}, so the sparse {@link
 * ClaimAmendmentPayload} round-trips its presence semantics: an omitted field stays absent and an
 * explicit {@code null} is written as {@code null}.
 */
@Component
@RequiredArgsConstructor
public class AmendmentJsonWriter {

  private final ObjectMapper objectMapper;

  /**
   * Serialises the before-state snapshot for the {@code before_state} column.
   *
   * @param beforeState the captured before-state
   * @return the JSON string
   */
  public String writeBeforeState(ClaimStateSnapshot beforeState) {
    return write(beforeState, "before_state");
  }

  /**
   * Serialises the sparse request payload for the {@code request_payload} column, preserving
   * omitted-vs-explicit-null.
   *
   * @param requestPayload the original sparse payload
   * @return the JSON string
   */
  public String writeRequestPayload(ClaimAmendmentPayload requestPayload) {
    return write(requestPayload, "request_payload");
  }

  /**
   * Serialises the assembled diff for the {@code diff} column.
   *
   * @param diff the assembled diff
   * @return the JSON string
   */
  public String writeDiff(AmendmentDiff diff) {
    return write(diff, "diff");
  }

  private String write(Object value, String columnName) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialise amendment " + columnName + " JSON", ex);
    }
  }
}
