package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;

/** Entity representing a bulk submission of claims. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bulk_submission")
public class BulkSubmission {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID) private UUID id;

  @NotNull
  @Type(JsonBinaryType.class)
  @JdbcTypeCode(SqlTypes.JSON)
  private BulkSubmissionDetails data;

  @NotNull
  @Enumerated(EnumType.STRING)
  private BulkSubmissionStatus status;

  private String errorCode;

  private String errorDescription;

  @NotNull private String createdByUserId;

  @NotNull
  @CreationTimestamp
  private Instant createdOn;

  private String updatedByUserId;

  @NotNull
  @UpdateTimestamp
  private Instant updatedOn;
}