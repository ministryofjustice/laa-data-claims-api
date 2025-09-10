package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
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

/** Entity representing client details linked to a claim. */
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
  private Claim claim;

  private String clientForename;

  private String clientSurname;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
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

  @Column(name = "client_2_forename")
  private String client2Forename;

  @Column(name = "client_2_surname")
  private String client2Surname;

  @Column(name = "client_2_date_of_birth")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
  private LocalDate client2DateOfBirth;

  @Column(name = "client_2_ucn")
  private String client2Ucn;

  @Column(name = "client_2_postcode")
  private String client2PostCode;

  @Column(name = "client_2_gender_code")
  private String client2GenderCode;

  @Column(name = "client_2_ethnicity_code")
  private String client2EthnicityCode;

  @Column(name = "client_2_disability_code")
  private String client2DisabilityCode;

  @Column(name = "client_2_is_legally_aided")
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
