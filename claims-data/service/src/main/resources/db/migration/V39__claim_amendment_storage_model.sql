-- -------------------------------------------------------------------
-- 1. Core: Claim Amendment
-- -------------------------------------------------------------------
CREATE TABLE claims.claim_amendment (
                                        id uuid PRIMARY KEY,
                                        claim_id uuid NOT NULL,
                                        amendment_reason_code varchar(50) NOT NULL,
                                        before_state jsonb NOT NULL,
                                        request_payload jsonb NOT NULL,
                                        diff jsonb NOT NULL,
                                        created_by_user_id text NOT NULL,
                                        created_on timestamptz NOT NULL DEFAULT now(),
                                        CONSTRAINT fk_claim_amendment_claim
                                            FOREIGN KEY (claim_id)
                                                REFERENCES claims.claim (id)
);

COMMENT ON TABLE claims.claim_amendment
    IS 'Business record of successful claim amendments including requester and before/after state';


-- -------------------------------------------------------------------
-- 2. Alter Existing Tables
-- -------------------------------------------------------------------
ALTER TABLE claims.calculated_fee_detail
    ADD COLUMN claim_amendment_id uuid,
    ADD COLUMN is_price_changed boolean,
    -- FIX: Adds the database level unique constraint to match Java's @OneToOne
    ADD CONSTRAINT uq_calculated_fee_detail_claim_amendment UNIQUE (claim_amendment_id);

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
-- 3. Indexes
-- -------------------------------------------------------------------
CREATE INDEX ix_claim_amendment_claim_id
    ON claims.claim_amendment (claim_id);

CREATE INDEX ix_claim_amendment_created_on
    ON claims.claim_amendment (created_on DESC);

CREATE INDEX ix_validation_message_log_claim_amendment_id
    ON claims.validation_message_log (claim_amendment_id);
