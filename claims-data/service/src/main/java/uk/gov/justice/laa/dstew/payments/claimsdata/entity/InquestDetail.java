package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Optional one-to-one scalar inquest details belonging to a claim. */
@Entity
@Table(name = "inquest_detail")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquestDetail {
  @Id private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "claim_id", nullable = false)
  private Claim claim;

  private String deceasedForename;
  private String deceasedSurname;
  private LocalDate deceasedDateOfBirth;
  private LocalDate deceasedDateOfDeath;
  private String coronersInquestReference;

  @Column(nullable = false)
  private String createdByUserId;

  @CreationTimestamp
  @Column(nullable = false)
  private Instant createdOn;

  private String updatedByUserId;
  @UpdateTimestamp private Instant updatedOn;
}
