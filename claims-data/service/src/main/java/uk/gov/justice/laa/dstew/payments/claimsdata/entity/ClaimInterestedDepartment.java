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

/** Link between a claim and a governed interested government department. */
@Entity
@Table(name = "claim_interested_department")
@Getter
@Setter
public class ClaimInterestedDepartment {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "claim_id", nullable = false)
  private Claim claim;

  private String departmentCode;
  private String createdByUserId;
  private Instant createdOn;
  private String updatedByUserId;
  private Instant updatedOn;
}
