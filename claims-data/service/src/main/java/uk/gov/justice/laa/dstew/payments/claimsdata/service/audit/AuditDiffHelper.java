package uk.gov.justice.laa.dstew.payments.claimsdata.service.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimAuditFieldDiff;

/** Helper for computing field-level diffs between JSON objects for audit purposes. */
@Component
public class AuditDiffHelper {
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Computes the field-level differences between two JSON strings, unless operation is INSERT or
   * DELETE.
   *
   * @param oldDataJson the original JSON string
   * @param newDataJson the updated JSON string
   * @param operation the audit operation (e.g., UPDATE, INSERT, DELETE)
   * @return a list of field diffs
   */
  public List<ClaimAuditFieldDiff> computeDiff(
      String oldDataJson, String newDataJson, String operation) {
    List<ClaimAuditFieldDiff> diffs = new ArrayList<>();
    if ("INSERT".equalsIgnoreCase(operation) || "DELETE".equalsIgnoreCase(operation)) {
      return diffs;
    }
    try {
      JsonNode oldNode =
          oldDataJson == null
              ? objectMapper.createObjectNode()
              : objectMapper.readTree(oldDataJson);
      JsonNode newNode =
          newDataJson == null
              ? objectMapper.createObjectNode()
              : objectMapper.readTree(newDataJson);
      JsonNode patch = JsonDiff.asJson(oldNode, newNode);
      for (JsonNode op : patch) {
        String opType = op.get("op").asText();
        String path = op.get("path").asText();
        String field = path.replaceFirst("/", "");
        String before = null;
        String after = null;
        if ("replace".equals(opType)) {
          // Fetch old value from oldNode
          JsonNode oldValueNode = oldNode.at(path);
          before = oldValueNode.isMissingNode() ? null : oldValueNode.asText(null);
          after = op.has("value") ? op.get("value").asText(null) : null;
        } else if ("add".equals(opType)) {
          before = null;
          after = op.has("value") ? op.get("value").asText(null) : null;
        } else if ("remove".equals(opType)) {
          JsonNode oldValueNode = oldNode.at(path);
          before = oldValueNode.isMissingNode() ? null : oldValueNode.asText(null);
          after = null;
        }
        if (before != null || after != null) {
          diffs.add(
              new ClaimAuditFieldDiff.Builder().field(field).before(before).after(after).build());
        }
      }
    } catch (Exception e) {
      // Handle or log exception as needed
    }
    return diffs;
  }
}
