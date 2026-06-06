-- Add credit card staging table to existing schema
CREATE TABLE IF NOT EXISTS finance_staging_cc_transaction (
    id               BIGSERIAL    PRIMARY KEY,
    source_file      VARCHAR(255) NULL,
    card_number      VARCHAR(30)  NULL,
    card_holder      VARCHAR(100) NULL,
    statement_date   DATE         NULL,
    original_date    DATE         NOT NULL,
    description      VARCHAR(255) NOT NULL,
    location         VARCHAR(100) NULL,
    ref_code         VARCHAR(30)  NULL,
    amount           NUMERIC(10,2) NOT NULL,
    installment_current  SMALLINT NULL,
    installment_total    SMALLINT NULL,
    installment_value    NUMERIC(10,2) NULL,
    type             VARCHAR(3)   NOT NULL,   -- 'OUT' purchase / 'IN' payment or credit
    is_processed     BOOLEAN      NOT NULL DEFAULT FALSE,
    assigned_category_id BIGINT REFERENCES finance_category(id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS finance_staging_cc_is_processed_idx
    ON finance_staging_cc_transaction (is_processed);
CREATE INDEX IF NOT EXISTS finance_staging_cc_original_date_idx
    ON finance_staging_cc_transaction (original_date);
CREATE INDEX IF NOT EXISTS finance_staging_cc_category_id_idx
    ON finance_staging_cc_transaction (assigned_category_id);
