package uk.gov.justice.laa.dstew.payments.claimsdata.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Simple DTO representing an audit event for a Claim. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimAuditEvent {
  private Instant timestamp;
  private String author;
  private String commitId;
  private String changeType; // INSERT, UPDATE, DELETE
  private List<ClaimAuditPropertyChange> changes;

  /** Represents a single property change within an audit event. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ClaimAuditPropertyChange {
    private String objectName;
    private String property;
    private Object oldValue;
    private Object newValue;
  }
}
