package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Reference table for Government Departments. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "government_department_ref", schema = "claims")
public class GovernmentDepartmentRef {

  @Id private UUID id;

  @NotNull
  @Column(name = "government_department_code", nullable = false, unique = true)
  private String governmentDepartmentCode;

  @NotNull
  @Column(name = "display_label", nullable = false)
  private String displayLabel;

  @NotNull
  @Column(nullable = false)
  private Boolean isActive;

  @NotNull
  @Column(nullable = false)
  private Integer displayOrder;

  @NotNull
  @Column(nullable = false)
  private String createdByUserId;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdOn;

  private String updatedByUserId;

  @UpdateTimestamp
  private Instant updatedOn;

  @OneToMany(mappedBy = "governmentDepartment")
  @Builder.Default
  private List<ClaimInterestedDepartment> claimInterestedDepartments = new ArrayList<>();
}
