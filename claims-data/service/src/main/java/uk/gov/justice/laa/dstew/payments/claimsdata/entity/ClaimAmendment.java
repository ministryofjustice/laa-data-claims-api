package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendedField;

/** Entity representing a claim amendment. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "claim_amendment")
public class ClaimAmendment {
  @Id
  @Column(name = "claim_amendment_id", nullable = false)
  private UUID claimAmendmentId;

  @Column(name = "claim_id", nullable = false)
  private UUID claimId;

  @Column(name = "created_by_user_id", nullable = false)
  private String createdByUserId;

  @CreationTimestamp
  @Column(name = "created_on", nullable = false)
  private OffsetDateTime createdOn;

  @Column(name = "updated_by_user_id")
  private String updatedByUserId;

  @UpdateTimestamp
  @Column(name = "updated_on")
  private OffsetDateTime updatedOn;

  @Column(name = "status", nullable = false)
  private String status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "changed_fields", columnDefinition = "jsonb", nullable = false)
  private List<AmendedField> changedFields;
}
