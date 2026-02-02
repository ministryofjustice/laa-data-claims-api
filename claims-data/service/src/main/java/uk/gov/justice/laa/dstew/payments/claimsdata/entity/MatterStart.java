package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import org.javers.core.metamodel.annotation.DiffIgnore;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.converter.MediationTypeConverter;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MediationType;

/** Entity representing a matter start linked to a submission. */
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

  private int numberOfMatterStarts;

  @Convert(converter = MediationTypeConverter.class)
  private MediationType mediationType;

  @Column(nullable = false)
  private String createdByUserId;

  @DiffIgnore
  @CreationTimestamp
  @Column(nullable = false)
  private Instant createdOn;

  @DiffIgnore private String updatedByUserId;

  @DiffIgnore
  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedOn;
}
