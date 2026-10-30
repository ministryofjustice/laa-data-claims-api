package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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

/** Spreadsheet and file-format metadata owned by a bulk upload or a webform submission. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "submission_metadata")
public class SubmissionMetadata {

  @Id private UUID id;

  @OneToOne
  @JoinColumn(name = "bulk_submission_id", unique = true)
  private BulkSubmission bulkSubmission;

  @Column(name = "submission_id", unique = true)
  private UUID submissionId;

  private String spreadsheetVersion;

  private String spreadsheetPlatform;

  private String sourceFileFormat;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdOn;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedOn;
}
