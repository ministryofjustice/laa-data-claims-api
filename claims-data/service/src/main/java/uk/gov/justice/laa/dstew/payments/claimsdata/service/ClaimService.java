package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.SubmissionClaim;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClientMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionClaimMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetSubmission200ResponseClaimsInner;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;

/**
 * Service containing business logic for handling claims.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimService {
  private final SubmissionRepository submissionRepository;
  private final SubmissionClaimRepository submissionClaimRepository;
  private final ClientRepository clientRepository;
  private final SubmissionClaimMapper submissionClaimMapper;
  private final ClientMapper clientMapper;

  /**
   * Create a claim for a submission.
   *
   * @param submissionId submission identifier
   * @param claimPost request payload
   * @return identifier of the created claim
   */
  @Transactional
  public UUID createClaim(UUID submissionId, ClaimPost claimPost) {
    Submission submission =
        submissionRepository
            .findById(submissionId)
            .orElseThrow(
                () ->
                    new SubmissionNotFoundException(
                        String.format("No submission found with id: %s", submissionId)));

    SubmissionClaim claim = submissionClaimMapper.toSubmissionClaim(claimPost);
    claim.setId(UUID.randomUUID());
    claim.setSubmission(submission);
    //  TODO: replace with the actual user ID/name when available
    claim.setCreatedByUserId("todo");
    submissionClaimRepository.save(claim);

    Client client = clientMapper.toClient(claimPost);
    if (hasClientData(client)) {
      client.setId(UUID.randomUUID());
      client.setClaim(claim);
      //  TODO: replace with the actual user ID/name when available
      client.setCreatedByUserId("todo");
      clientRepository.save(client);
    }

    return claim.getId();
  }

  /**
   * Retrieve a claim for a submission.
   *
   * @param submissionId submission identifier
   * @param claimId claim identifier
   * @return populated claim fields
   */
  @Transactional(readOnly = true)
  public ClaimFields getClaim(UUID submissionId, UUID claimId) {
    SubmissionClaim claim =
        submissionClaimRepository
            .findByIdAndSubmissionId(claimId, submissionId)
            .orElseThrow(
                () ->
                    new ClaimNotFoundException(
                        String.format("No claim %s for submission %s", claimId, submissionId)));

    ClaimFields fields = submissionClaimMapper.toClaimFields(claim);
    clientRepository
        .findByClaimId(claimId)
        .ifPresent(client -> clientMapper.updateClaimFieldsFromClient(client, fields));
    return fields;
  }

  /**
   * Update a claim for a submission.
   *
   * @param submissionId submission identifier
   * @param claimId claim identifier
   * @param claimPatch patch payload
   */
  @Transactional
  public void updateClaim(UUID submissionId, UUID claimId, ClaimPatch claimPatch) {
    SubmissionClaim claim =
        submissionClaimRepository
            .findByIdAndSubmissionId(claimId, submissionId)
            .orElseThrow(
                () ->
                    new ClaimNotFoundException(
                        String.format("No claim %s for submission %s", claimId, submissionId)));

    submissionClaimMapper.updateSubmissionClaimFromPatch(claimPatch, claim);
    submissionClaimRepository.save(claim);
  }

  /**
   * Retrieve claim summaries for a submission.
   *
   * @param submissionId submission identifier
   * @return list of claim summary records
   */
  @Transactional(readOnly = true)
  public List<GetSubmission200ResponseClaimsInner> getClaimsForSubmission(UUID submissionId) {
    return submissionClaimRepository.findBySubmissionId(submissionId).stream()
        .map(submissionClaimMapper::toGetSubmission200ResponseClaimsInner)
        .toList();
  }

  private boolean hasClientData(Client client) {
    return client.getClientForename() != null
        || client.getClientSurname() != null
        || client.getClientDateOfBirth() != null
        || client.getClient2Forename() != null
        || client.getClient2Surname() != null
        || client.getClient2DateOfBirth() != null;
  }
}
