package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Entity representing interested Government Department. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "claim_interested_department",
    schema = "claims",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_claim_interested_department_claim_id_display_order",
            columnNames = {"claim_id", "display_order"}))
public class ClaimInterestedDepartment {

  @Id private UUID id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "claim_id", nullable = false)
  private Claim claim;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "government_department_id", nullable = false)
  private GovernmentDepartmentRef governmentDepartment;

  @NotNull
  @Column(nullable = false)
  private Integer displayOrder;

  @NotNull
  @Column(nullable = false)
  private String createdByUserId;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdOn;

  private String updatedByUserId;

  @UpdateTimestamp
  private Instant updatedOn;
}
