package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.javers.core.metamodel.annotation.DiffIgnore;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionErrorCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;

/** Entity representing a bulk submission of claims. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bulk_submission")
public class BulkSubmission {

  @Id private UUID id;

  @DiffIgnore
  @NotNull
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private GetBulkSubmission200ResponseDetails data;

  // It already lives in claims. Keeping it in javers doubles the storage for something
  // we don’t diff at field-level anyway.

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BulkSubmissionStatus status;

  @Enumerated(EnumType.STRING)
  private BulkSubmissionErrorCode errorCode;

  private String errorDescription;

  @Column(nullable = false)
  private String createdByUserId;

  @DiffIgnore
  @CreationTimestamp
  @Column(nullable = false)
  private Instant createdOn;

  @DiffIgnore private String updatedByUserId;

  private String authorisedOffices;

  @DiffIgnore
  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedOn;
}
