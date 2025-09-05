package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;

/** Repository for managing Submission entities. */
@Repository
public interface SubmissionRepository
    extends JpaRepository<Submission, UUID>, JpaSpecificationExecutor<Submission> {}
