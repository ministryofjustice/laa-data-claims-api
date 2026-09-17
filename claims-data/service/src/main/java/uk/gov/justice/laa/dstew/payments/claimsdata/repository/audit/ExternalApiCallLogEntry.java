package uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code audit.external_api_call_log}: a single outbound external API call as made.
 */
public record ExternalApiCallLogEntry(
    UUID id,
    ExternalSystemType systemType,
    String endpoint,
    String httpMethod,
    String requestPayload,
    String responsePayload,
    Integer httpStatus,
    UUID submissionId,
    UUID claimId,
    Instant createdOn) {}
