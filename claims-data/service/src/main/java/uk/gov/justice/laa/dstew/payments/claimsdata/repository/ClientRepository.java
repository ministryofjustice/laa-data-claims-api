package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;

/** Repository for accessing client records. */
public interface ClientRepository extends JpaRepository<Client, UUID> {
  Optional<Client> findByClaimId(UUID claimId);
}

