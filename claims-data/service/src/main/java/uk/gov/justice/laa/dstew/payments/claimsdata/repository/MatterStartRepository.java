package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.MatterStart;

/**
 * Repository for managing matter start entities.
 */
@Repository
public interface MatterStartRepository extends JpaRepository<MatterStart, UUID> {

  /**
   * Finds a list of matter starts by submission id.
   *
   * @param submissionId submission identifier
   * @return list of matter starts
   */
  List<MatterStart> findBySubmissionId(UUID submissionId);

  /**
   * Finds a matter start by submission id and id.
   *
   * @param submissionId submission identifier
   * @param id matter start identifier
   * @return matter start
   */
  Optional<MatterStart> findBySubmissionIdAndId(UUID submissionId, UUID id);
}

