package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.SubmissionMetadata;

/** Repository for metadata owned by submissions and bulk uploads. */
public interface SubmissionMetadataRepository extends JpaRepository<SubmissionMetadata, UUID> {}
