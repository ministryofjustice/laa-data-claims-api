package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;

/** Repository for handling CRUD operations on calculated fee detail records. */
@Repository
public interface CalculatedFeeDetailRepository extends JpaRepository<CalculatedFeeDetail, UUID> {}
