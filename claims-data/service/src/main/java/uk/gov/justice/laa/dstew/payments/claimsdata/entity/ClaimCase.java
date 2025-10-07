package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Entity representing the summary of the claim case. */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "claim_case", schema = "claims")
public class ClaimCase {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "claim_id", nullable = false)
  private Claim claim;

  private String caseId;

  private String uniqueCaseId;

  private String caseStageCode;

  private String stageReachedCode;

  private String standardFeeCategoryCode;

  @NotNull
  @Column(nullable = false)
  private String outcomeCode;

  private String designatedAccreditedRepresentativeCode;

  private Boolean isPostalApplicationAccepted;

  @Column(name = "is_client_2_postal_application_accepted")
  private Boolean isClient2PostalApplicationAccepted;

  private String mentalHealthTribunalReference;

  private Boolean isNrmAdvice;

  private String followOnWork;

  private LocalDate transferDate;

  private String exemptionCriteriaSatisfied;

  private String exceptionalCaseFundingReference;

  private Boolean isLegacyCase;

  @Column(nullable = false)
  private String createdByUserId;

  @CreationTimestamp
  @Column(nullable = false)
  private OffsetDateTime createdOn;

  private String updatedByUserId;

  @UpdateTimestamp private OffsetDateTime updatedOn;
}
