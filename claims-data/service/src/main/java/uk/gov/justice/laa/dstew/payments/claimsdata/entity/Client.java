package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

/**
 * Entity representing client details linked to a claim.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "client")
public class Client {

  @Id private UUID id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "claim_id", nullable = false)
  private SubmissionClaim claim;

  private String clientForename;

  private String clientSurname;

  private LocalDate clientDateOfBirth;

  private String uniqueClientNumber;

  private String clientPostcode;

  private String genderCode;

  private String ethnicityCode;

  private String disabilityCode;

  private Boolean isLegallyAided;

  private String clientTypeCode;

  private String homeOfficeClientNumber;

  private String claReferenceNumber;

  private String claExemptionCode;

  private String client2Forename;

  private String client2Surname;

  private LocalDate client2DateOfBirth;

  private String client2Ucn;

  private String client2Postcode;

  private String client2GenderCode;

  private String client2EthnicityCode;

  private String client2DisabilityCode;

  private Boolean client2IsLegallyAided;

  @Column(nullable = false)
  private String createdByUserId;

  @CreationTimestamp
  @Column(nullable = false)
  private Instant createdOn;

  private String updatedByUserId;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedOn;
}

