package uk.gov.justice.laa.dstew.payments.claimsdata.repository.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code audit.external_api_call_log}: a single outbound external API call as made.
 *
 * @param id
 * @param systemType
 * @param endpoint
 * @param httpMethod
 * @param requestPayload
 * @param responsePayload
 * @param httpStatus
 * @param submissionId
 * @param claimId
 * @param createdOn
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
