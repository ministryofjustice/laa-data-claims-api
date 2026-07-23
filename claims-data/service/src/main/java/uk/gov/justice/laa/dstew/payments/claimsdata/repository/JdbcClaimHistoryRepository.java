package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimHistoryEventRowMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;

/**
 * PostgreSQL implementation of {@link ClaimHistoryRepository}.
 *
 * <p>The entire timeline is produced by a <strong>single</strong> {@code UNION ALL} query that
 * projects each source table into the common {@link ClaimHistoryEventRow} shape and builds
 * event-specific attributes server-side with {@code jsonb_build_object}. No entities are loaded and
 * no results are merged in Java.
 *
 * <p><b>Ordering</b> is purely chronological — {@code event_timestamp DESC} — with the UUIDv7
 * {@code source_id DESC} as a deterministic tie-breaker. Event type never influences ordering.
 *
 * <p><b>Pagination:</b> only a {@code LIMIT} is applied (no {@code OFFSET}). The {@code
 * (event_timestamp, source_id)} ordering key is exactly the seed required for keyset/cursor
 * pagination: a future overload only has to add {@code AND (created_on, id) < (:cursorTimestamp,
 * :cursorSourceId)} to each branch (see {@link #CURSOR_PREDICATE}) without any structural redesign.
 */
@Repository
public class JdbcClaimHistoryRepository implements ClaimHistoryRepository {

  /**
   * Keyset predicate to append to each branch's {@code WHERE} clause for cursor pagination.
   * Applying it per-branch (rather than to the merged result) lets each source use its composite
   * {@code (claim_id, created_on DESC, id DESC)} index.
   */
  static final String CURSOR_PREDICATE =
      " AND (created_on, id) < (:cursorTimestamp, :cursorSourceId)";

  /**
   * Single unified timeline query.
   *
   * <p>Each {@code SELECT} yields the identical column list {@code (event_type, event_timestamp,
   * actor_id, source_id, metadata)} so the branches are union-compatible. {@code actor_id} is
   * always populated via {@code COALESCE(..., 'SYSTEM')}. Assessment rows with {@code
   * assessment_type = 'VOID'} are surfaced as a {@code VOID} event carrying only {@code
   * assessment_type} and {@code assessment_reason}; all other assessments become {@code ASSESSMENT}
   * events carrying {@code assessment_type}, {@code assessment_outcome} and {@code
   * assessment_reason}. Amendment events expose the requester and reason, pricing/escape flags
   * derived from the linked {@code calculated_fee_detail}, and the field-level {@code changes}
   * array lifted from the amendment {@code diff} document.
   */
  private static final String HISTORY_SQL =
      """
      SELECT event_type, event_timestamp, actor_id, source_id, metadata
      FROM (
          -- ---------- SUBMISSION (source: claim) ----------
          SELECT
              'SUBMISSION'                       AS event_type,
              c.created_on                       AS event_timestamp,
              COALESCE(c.created_by_user_id, 'SYSTEM') AS actor_id,
              c.id                               AS source_id,
              jsonb_build_object(
                  'submission_period',     s.submission_period,
                  'office_account_number', s.office_account_number,
                  'area_of_law',           s.area_of_law
              )                                  AS metadata
          FROM claims.claim c
          JOIN claims.submission s ON s.id = c.submission_id
          WHERE c.id = :claimId

          UNION ALL

          -- ---------- AMENDMENT (source: claim_amendment) ----------
          SELECT
              'AMENDMENT'                        AS event_type,
              am.created_on                      AS event_timestamp,
              COALESCE(am.created_by_user_id, 'SYSTEM') AS actor_id,
              am.id                              AS source_id,
              jsonb_build_object(
                  'requested_by_code',    am.requested_by_code,
                  'amendment_reason_code', am.amendment_reason_code,
                  'pricing_recalculated', (cfd.id IS NOT NULL),
                  'price_changed',        COALESCE(cfd.is_price_changed, false),
                  'escape_case_logged',   COALESCE(cfd.escape_case_flag, false),
                  'changes',              COALESCE(am.diff -> 'changes', '[]'::jsonb)
              )                                  AS metadata
          FROM claims.claim_amendment am
          LEFT JOIN claims.calculated_fee_detail cfd ON cfd.claim_amendment_id = am.id
          WHERE am.claim_id = :claimId

          UNION ALL

          -- ---------- ASSESSMENT (source: assessment) ----------
          SELECT
              CASE WHEN asmt.assessment_type = 'VOID' THEN 'VOID' ELSE 'ASSESSMENT' END
                                                 AS event_type,
              asmt.created_on                    AS event_timestamp,
              COALESCE(asmt.created_by_user_id, 'SYSTEM') AS actor_id,
              asmt.id                            AS source_id,
              CASE
                  WHEN asmt.assessment_type = 'VOID' THEN
                      jsonb_build_object(
                          'assessment_type',   asmt.assessment_type,
                          'assessment_reason', asmt.assessment_reason
                      )
                  ELSE
                      jsonb_build_object(
                          'assessment_type',    asmt.assessment_type,
                          'assessment_outcome', asmt.assessment_outcome,
                          'assessment_reason',  asmt.assessment_reason
                      )
              END                                AS metadata
          FROM claims.assessment asmt
          WHERE asmt.claim_id = :claimId
      ) AS claim_history
      ORDER BY event_timestamp DESC, source_id DESC
      LIMIT :limit
      """;

  private final JdbcClient jdbcClient;
  private final ClaimHistoryEventRowMapper rowMapper;

  public JdbcClaimHistoryRepository(JdbcClient jdbcClient, ClaimHistoryEventRowMapper rowMapper) {
    this.jdbcClient = jdbcClient;
    this.rowMapper = rowMapper;
  }

  @Override
  public List<ClaimHistoryEventRow> findHistory(UUID claimId, int limit) {
    return jdbcClient
        .sql(HISTORY_SQL)
        .param("claimId", claimId)
        .param("limit", limit)
        .query(rowMapper)
        .list();
  }
}
