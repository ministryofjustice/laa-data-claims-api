package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/** Entity representing an audit log entry for changes to database tables. */
@Getter
@Setter
@Entity
@Table(name = "audit_log", schema = "audit")
public class AuditLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "table_name", nullable = false)
  private String tableName;

  @Column(name = "operation", nullable = false)
  private String operation;

  @Column(name = "primary_key", columnDefinition = "jsonb", nullable = false)
  private String primaryKey;

  @Column(name = "old_data", columnDefinition = "jsonb")
  private String oldData;

  @Column(name = "new_data", columnDefinition = "jsonb")
  private String newData;

  @Column(name = "changed_at", nullable = false)
  private OffsetDateTime changedAt;

  @Column(name = "actor_user")
  private String actorUser;

  @Column(name = "actor_service", nullable = false)
  private String actorService;

  @Column(name = "metadata", columnDefinition = "jsonb")
  private String metadata;

  // Getters and setters omitted for brevity
}
