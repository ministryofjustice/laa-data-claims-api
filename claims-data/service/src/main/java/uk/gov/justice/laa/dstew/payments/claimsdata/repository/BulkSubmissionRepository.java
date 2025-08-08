package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;

@Repository
public interface BulkSubmissionRepository extends JpaRepository<BulkSubmission, Long> {
}
