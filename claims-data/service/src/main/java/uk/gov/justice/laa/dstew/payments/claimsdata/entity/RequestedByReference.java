package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Entity representing a requested by reference. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "requested_by_reference", schema = "claims")
public class RequestedByReference {
  @Id private UUID id; // UUIDv7 [cite: 186]

  @NotNull
  @Column(unique = true, nullable = false)
  private String code; // Stable machine code [cite: 187]

  @NotNull private String displayLabel; // Human-readable [cite: 189]

  @NotNull private Boolean isActive = true; // For retirement [cite: 190]

  @NotNull private Integer displayOrder; // UI ordering [cite: 192]

  private OffsetDateTime createdOn;
  private OffsetDateTime updatedOn;
}
