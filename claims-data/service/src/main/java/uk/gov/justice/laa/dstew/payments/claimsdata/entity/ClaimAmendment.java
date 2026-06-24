package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
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
  @Id private UUID id; // UUIDv7

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "claim_id", nullable = false)
  private Claim claim; // The amended claim

  /**
   * Requesting party for the amendment. Validated by a foreign key to {@code
   * requested_by_reference.code} (governed reference data).
   */
  @NotNull
  @Column(name = "requested_by_code", nullable = false)
  private String requestedByCode;

  /**
   * Amendment reason. Validated by a composite foreign key to {@code amendment_reason_reference
   * (requested_by_code, code)}, so the reason must be valid for the requesting party.
   */
  @NotNull
  @Column(name = "amendment_reason_code", nullable = false)
  private String amendmentReasonCode;

  @NotNull
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String beforeState; // Snapshot

  @NotNull
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String requestPayload; // Sparse payload

  @NotNull
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String diff; // Versioned changes

  @NotNull private String createdByUserId; // Entra UUID

  @NotNull private OffsetDateTime createdOn; // Timestamp

  @OneToOne(mappedBy = "claimAmendment")
  private CalculatedFeeDetail calculatedFeeDetail;
}
