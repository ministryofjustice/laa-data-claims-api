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
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entity representing a matter start linked to a submission.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "matter_start")
public class MatterStart {

  @Id private UUID id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "submission_id", nullable = false)
  private Submission submission;

  private String scheduleReference;

  private String categoryCode;

  private String procurementAreaCode;

  private String accessPointCode;

  private String deliveryLocation;

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

