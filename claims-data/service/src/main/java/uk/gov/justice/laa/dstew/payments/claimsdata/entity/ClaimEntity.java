package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The entity class for claims.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "CLAIMS")
public class ClaimEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

  private String name;

  private String description;
}
