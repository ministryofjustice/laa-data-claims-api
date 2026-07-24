-- The claim's effective (most recent priced) value, computed on read.
-- Precedence: the allowed total (incl. VAT) of the latest assessment when the claim has any
-- assessment (0 for a void assessment, which stores allowed_total_incl_vat = 0); otherwise the
-- latest calculated fee total amount (initial pricing or an amendment reprice); otherwise null.
-- The Claim entity reads this via an @Formula correlated subquery, so no column is stored on claim.
-- Objects are schema-qualified (claims.*) so the view resolves regardless of the connection
-- search_path, matching the raw-SQL convention used elsewhere in this codebase.
CREATE VIEW claims.vw_claim_effective_value AS
SELECT c.id AS claim_id,
       COALESCE(
           (SELECT a.allowed_total_incl_vat
            FROM claims.assessment a
            WHERE a.claim_id = c.id
            ORDER BY a.created_on DESC, a.id DESC
            LIMIT 1),
           (SELECT cfd.total_amount
            FROM claims.calculated_fee_detail cfd
            WHERE cfd.claim_id = c.id
            ORDER BY cfd.created_on DESC, cfd.id DESC
            LIMIT 1)
       ) AS effective_total_value
FROM claims.claim c;

