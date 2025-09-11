package uk.gov.justice.laa.dstew.payments.claimsevent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/**
 * Record for deserializing a bulk submission message from an SQS queue.
 *
 * @param bulkSubmissionId the bulk submission ID
 * @param submissionIds the list of submission ID's
 * @author Jamie Briggs
 */
public record BulkSubmissionMessage(
    @JsonProperty("bulk_submission_id") UUID bulkSubmissionId,
    @JsonProperty("submission_ids") List<UUID> submissionIds) {}
