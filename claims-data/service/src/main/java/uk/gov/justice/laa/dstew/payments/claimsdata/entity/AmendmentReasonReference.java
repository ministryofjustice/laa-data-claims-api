package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

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

/** Entity representing an amendment reason reference. */
@Getter
@Setter
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "amendment_reason_reference", schema = "claims")
public class AmendmentReasonReference {
  @Id private UUID id; // UUIDv7 [cite: 194]

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "requested_by_reference_id",
      nullable = false) // Explicitly point to the actual DB column
  private RequestedByReference requestedBy; // Scoped party [cite: 195]

  @NotNull private String code; // Stable code [cite: 196]

  @NotNull private String displayLabel;

  @NotNull private Boolean isActive = true;

  @NotNull private Integer displayOrder;

  private OffsetDateTime createdOn;
  private OffsetDateTime updatedOn;
}
