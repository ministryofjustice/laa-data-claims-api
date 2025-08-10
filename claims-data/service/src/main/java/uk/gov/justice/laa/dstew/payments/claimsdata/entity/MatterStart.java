package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "submission_id", nullable = false)
  private Submission submission;

  @Column(name = "schedule_reference")
  private String scheduleReference;

  @Column(name = "category_code")
  private String categoryCode;

  @Column(name = "procurement_area_code")
  private String procurementAreaCode;

  @Column(name = "access_point_code")
  private String accessPointCode;

  @Column(name = "delivery_location")
  private String deliveryLocation;

  @Column(name = "number_of_matter_starts")
  private Integer numberOfMatterStarts;

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

