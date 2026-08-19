package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Ordered free-text public authority interested in an inquest claim. */
@Entity
@Table(name = "claim_interested_public_authority")
@Getter
@Setter
public class ClaimInterestedPublicAuthority {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "claim_id", nullable = false)
  private Claim claim;

  private String authorityName;
  private Integer displayOrder;
  private String createdByUserId;
  private Instant createdOn;
  private String updatedByUserId;
  private Instant updatedOn;
}
