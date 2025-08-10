package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "claim_id", nullable = false)
  private SubmissionClaim claim;

  @Column(name = "client_forename")
  private String clientForename;

  @Column(name = "client_surname")
  private String clientSurname;

  @Column(name = "client_date_of_birth")
  private LocalDate clientDateOfBirth;

  @Column(name = "unique_client_number")
  private String uniqueClientNumber;

  @Column(name = "client_postcode")
  private String clientPostcode;

  @Column(name = "gender_code")
  private String genderCode;

  @Column(name = "ethnicity_code")
  private String ethnicityCode;

  @Column(name = "disability_code")
  private String disabilityCode;

  @Column(name = "is_legally_aided")
  private Boolean isLegallyAided;

  @Column(name = "client_type_code")
  private String clientTypeCode;

  @Column(name = "home_office_client_number")
  private String homeOfficeClientNumber;

  @Column(name = "cla_reference_number")
  private String claReferenceNumber;

  @Column(name = "cla_exemption_code")
  private String claExemptionCode;

  @Column(name = "client_2_forename")
  private String client2Forename;

  @Column(name = "client_2_surname")
  private String client2Surname;

  @Column(name = "client_2_date_of_birth")
  private LocalDate client2DateOfBirth;

  @Column(name = "client_2_ucn")
  private String client2Ucn;

  @Column(name = "client_2_postcode")
  private String client2Postcode;

  @Column(name = "client_2_gender_code")
  private String client2GenderCode;

  @Column(name = "client_2_ethnicity_code")
  private String client2EthnicityCode;

  @Column(name = "client_2_disability_code")
  private String client2DisabilityCode;

  @Column(name = "client_2_is_legally_aided")
  private Boolean client2IsLegallyAided;

  @Column(name = "created_by_user_id", nullable = false)
  private String createdByUserId;

  @CreationTimestamp
  @Column(name = "created_on", nullable = false)
  private Instant createdOn;

  @Column(name = "updated_by_user_id")
  private String updatedByUserId;

  @UpdateTimestamp
  @Column(name = "updated_on")
  private Instant updatedOn;
}

