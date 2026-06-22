package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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

/**
 * Governed reference data entity for an amendment reason, scoped to the "Requested By" value it is
 * valid for.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "amendment_reason_reference",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_amendment_reason_reference_party_code",
            columnNames = {"requested_by_code", "code"}))
public class AmendmentReasonReferenceEntity {

  // Surrogate key kept for schema-wide consistency (every entity uses a UUIDv7 id) and to avoid a
  // composite key class. The business key is (requested_by_code, code), which is the unique
  // constraint that claim_amendment's composite FK targets; nothing references this id.
  // TODO(DSTEW-1594): revisit in review - the natural composite key could serve as the PK.
  @Id private UUID id;

  @NotNull
  @Column(name = "requested_by_code", nullable = false)
  private String requestedByCode;

  @NotNull
  @Column(nullable = false)
  private String code;

  @NotNull
  @Column(nullable = false)
  private String displayLabel;

  @NotNull
  @Column(nullable = false)
  private Boolean isActive;

  @NotNull
  @Column(nullable = false)
  private Integer displayOrder;

  @NotNull
  @Column(nullable = false)
  private String createdByUserId;

  @CreationTimestamp
  @Column(nullable = false)
  private Instant createdOn;

  private String updatedByUserId;

  @UpdateTimestamp private Instant updatedOn;
}
