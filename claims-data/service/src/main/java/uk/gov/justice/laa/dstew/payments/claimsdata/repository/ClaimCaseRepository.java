package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;

/** Repository for handling CRUD operations on claim case records. */
public interface ClaimCaseRepository extends JpaRepository<ClaimCase, Long> {}
