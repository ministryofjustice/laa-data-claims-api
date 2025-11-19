package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static uk.gov.justice.laa.dstew.payments.claimsdata.service.lookup.EntityLookup.requireEntity;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimSummaryFeeNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.AssessmentMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AssessmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;

/** Service containing business logic for handling assessments. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentService {

  private final ClaimRepository claimRepository;
  private final ClaimSummaryFeeRepository claimSummaryFeeRepository;
  private final AssessmentRepository assessmentRepository;
  private final AssessmentMapper assessmentMapper;

  public UUID createAssessment(AssessmentPost request) {
    Claim claim =
        requireEntity(
            claimRepository, request.getClaimId(), x -> () -> new ClaimNotFoundException(x));

    ClaimSummaryFee claimSummaryFee =
        requireEntity(
            claimSummaryFeeRepository,
            request.getClaimSummaryFeeId(),
            x -> () -> new ClaimSummaryFeeNotFoundException(x));

    Assessment assessment = assessmentMapper.toAssessment(request);
    assessment.setClaim(claim);
    assessment.setClaimSummaryFee(claimSummaryFee);

    return assessmentRepository.save(assessment).getId();
  }
}
