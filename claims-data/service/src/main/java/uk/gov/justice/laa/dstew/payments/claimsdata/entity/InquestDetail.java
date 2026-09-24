package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
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

/** Entity representing Inquest details. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inquest_detail", schema = "claims")
public class InquestDetail {

  @Id private UUID id;

  @NotNull
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(nullable = false, unique = true)
  private Claim claim;

  private String deceasedForename;

  private String deceasedSurname;

  private LocalDate deceasedDateOfDeath;

  private String coronersInquestReference;

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
