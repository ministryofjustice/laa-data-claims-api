-- -------------------------------------------------------------------
-- 1. Metadata: Requested By Reference
-- -------------------------------------------------------------------
CREATE TABLE claims.requested_by_reference (
                                               id uuid PRIMARY KEY,
                                               code text NOT NULL,
                                               display_label text NOT NULL,
                                               is_active boolean NOT NULL DEFAULT true,
                                               display_order integer NOT NULL,
                                               created_on timestamptz NOT NULL DEFAULT now(),
                                               updated_on timestamptz NOT NULL DEFAULT now(),
                                               CONSTRAINT uq_requested_by_reference_code
                                                   UNIQUE (code)
);

COMMENT ON TABLE claims.requested_by_reference
IS 'Governed list of parties/types authorized to request claim amendments';


-- -------------------------------------------------------------------
-- 2. Metadata: Amendment Reason Reference
-- -------------------------------------------------------------------
CREATE TABLE claims.amendment_reason_reference (
                                                   id uuid PRIMARY KEY,
                                                   requested_by_reference_id uuid NOT NULL,
                                                   code text NOT NULL,
                                                   display_label text NOT NULL,
                                                   is_active boolean NOT NULL DEFAULT true,
                                                   display_order integer NOT NULL,
                                                   created_on timestamptz NOT NULL DEFAULT now(),
                                                   updated_on timestamptz NOT NULL DEFAULT now(),
                                                   CONSTRAINT fk_amendment_reason_reference_requested_by_reference
                                                       FOREIGN KEY (requested_by_reference_id)
                                                           REFERENCES claims.requested_by_reference (id),
                                                   CONSTRAINT uq_amendment_reason_reference_requester_code
                                                       UNIQUE (requested_by_reference_id, code)
);

COMMENT ON TABLE claims.amendment_reason_reference
IS 'Valid amendment reasons scoped to a requesting party';


-- -------------------------------------------------------------------
-- 3. Core: Claim Amendment
-- -------------------------------------------------------------------
CREATE TABLE claims.claim_amendment (
                                        id uuid PRIMARY KEY,
                                        claim_id uuid NOT NULL,
                                        amendment_reason_reference_id uuid NOT NULL,
                                        before_state jsonb NOT NULL,
                                        request_payload jsonb NOT NULL,
                                        diff jsonb NOT NULL,
                                        created_by_user_id text NOT NULL,
                                        created_on timestamptz NOT NULL DEFAULT now(),
                                        CONSTRAINT fk_claim_amendment_claim
                                            FOREIGN KEY (claim_id)
                                                REFERENCES claims.claim (id),
                                        CONSTRAINT fk_claim_amendment_amendment_reason_reference
                                            FOREIGN KEY (amendment_reason_reference_id)
                                                REFERENCES claims.amendment_reason_reference (id)
);

COMMENT ON TABLE claims.claim_amendment
IS 'Business record of successful claim amendments including requester and before/after state';


-- -------------------------------------------------------------------
-- 4. Alter Existing Tables
-- -------------------------------------------------------------------
ALTER TABLE claims.calculated_fee_detail
    ADD COLUMN claim_amendment_id uuid,
    ADD COLUMN is_price_changed boolean;

ALTER TABLE claims.calculated_fee_detail
    ADD CONSTRAINT fk_calculated_fee_detail_claim_amendment
        FOREIGN KEY (claim_amendment_id)
            REFERENCES claims.claim_amendment (id);

COMMENT ON COLUMN claims.calculated_fee_detail.is_price_changed
IS 'Flag indicating whether repricing resulted in a monetary change';


ALTER TABLE claims.validation_message_log
    ADD COLUMN claim_amendment_id uuid;

ALTER TABLE claims.validation_message_log
    ADD CONSTRAINT fk_validation_message_log_claim_amendment
        FOREIGN KEY (claim_amendment_id)
            REFERENCES claims.claim_amendment (id);


-- -------------------------------------------------------------------
-- 5. Indexes
-- -------------------------------------------------------------------
CREATE INDEX ix_claim_amendment_claim_id
    ON claims.claim_amendment (claim_id);

CREATE INDEX ix_claim_amendment_created_on
    ON claims.claim_amendment (created_on DESC);

CREATE INDEX ix_calculated_fee_detail_claim_amendment_id
    ON claims.calculated_fee_detail (claim_amendment_id);

CREATE INDEX ix_validation_message_log_claim_amendment_id
    ON claims.validation_message_log (claim_amendment_id);
