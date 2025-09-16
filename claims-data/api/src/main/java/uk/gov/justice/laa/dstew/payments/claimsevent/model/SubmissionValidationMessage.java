package uk.gov.justice.laa.dstew.payments.claimsevent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Record for deserializing a submission validation message from an SQS queue.
 *
 * @param submissionId the ID of the submission to validate
 */
public record SubmissionValidationMessage(@JsonProperty("submission_id") UUID submissionId) {}
