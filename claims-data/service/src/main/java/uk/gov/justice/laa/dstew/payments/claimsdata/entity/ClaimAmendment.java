package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Entity representing a claim amendment. */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "claim_amendment", schema = "claims")
public class ClaimAmendment {
  @Id private UUID id; // UUIDv7 [cite: 519]

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "claim_id", nullable = false)
  private Claim claim; // The amended claim [cite: 520]

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "amendment_reason_reference_id", nullable = false)
  private AmendmentReasonReference amendmentReason; // Metadata anchor [cite: 522, 593]

  @NotNull
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String beforeState; // Snapshot [cite: 523]

  @NotNull
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String requestPayload; // Sparse payload [cite: 524]

  @NotNull
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String diff; // Versioned changes [cite: 525, 532]

  @NotNull private String createdByUserId; // Entra UUID [cite: 526]

  @NotNull private OffsetDateTime createdOn; // Timestamp [cite: 527]
}
