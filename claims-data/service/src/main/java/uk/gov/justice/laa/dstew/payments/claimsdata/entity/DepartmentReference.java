package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Governed government department that may be interested in an inquest claim. */
@Entity
@Table(name = "department_reference")
@Getter
@Setter
public class DepartmentReference {
  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String code;

  @Column(nullable = false)
  private String displayLabel;

  @Column(nullable = false)
  private Boolean isActive;

  @Column(nullable = false)
  private Integer displayOrder;

  @Column(nullable = false)
  private String createdByUserId;

  @Column(nullable = false)
  private Instant createdOn;

  private String updatedByUserId;
  private Instant updatedOn;
}
