package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

/** Governed reference data entity for an amendment "Requested By" value. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "requested_by_reference")
public class RequestedByReferenceEntity {

  // Surrogate key kept for schema-wide consistency (every entity uses a UUIDv7 id). The business
  // key is `code` (unique) - that is what other tables reference via FK; nothing references this
  // id.
  // TODO(DSTEW-1594): revisit in review - `code` alone could serve as the PK for this table.
  @Id private UUID id;

  @NotNull
  @Column(nullable = false, unique = true)
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
