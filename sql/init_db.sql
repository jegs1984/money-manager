CREATE TABLE IF NOT EXISTS finance_period (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT finance_period_name_unique UNIQUE (name)
);
CREATE INDEX IF NOT EXISTS finance_period_start_date_idx ON finance_period (start_date);
CREATE INDEX IF NOT EXISTS finance_period_end_date_idx   ON finance_period (end_date);

CREATE TABLE IF NOT EXISTS finance_category (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    "group" VARCHAR(100) NOT NULL,
    CONSTRAINT finance_category_name_unique UNIQUE (name)
);
CREATE INDEX IF NOT EXISTS finance_category_name_idx ON finance_category (name);

CREATE TABLE IF NOT EXISTS finance_budget_item (
    id BIGSERIAL PRIMARY KEY,
    period_id   BIGINT NOT NULL REFERENCES finance_period(id)   ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES finance_category(id) ON DELETE PROTECT,
    type VARCHAR(3) NOT NULL,
    projected_amount NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    CONSTRAINT finance_budget_item_projected_amount_gte_0      CHECK (projected_amount >= 0),
    CONSTRAINT finance_budget_item_period_category_unique UNIQUE (period_id, category_id)
);
CREATE INDEX IF NOT EXISTS finance_budget_item_period_id_idx   ON finance_budget_item (period_id);
CREATE INDEX IF NOT EXISTS finance_budget_item_category_id_idx ON finance_budget_item (category_id);

CREATE TABLE IF NOT EXISTS finance_transaction (
    id BIGSERIAL PRIMARY KEY,
    budget_item_id BIGINT REFERENCES finance_budget_item(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    real_amount NUMERIC(10,2) NOT NULL,
    description VARCHAR(255) NOT NULL,
    notes TEXT
);
CREATE INDEX IF NOT EXISTS finance_transaction_date_idx           ON finance_transaction (date);
CREATE INDEX IF NOT EXISTS finance_transaction_budget_item_id_idx ON finance_transaction (budget_item_id);

CREATE TABLE IF NOT EXISTS finance_staging_transaction (
    id BIGSERIAL PRIMARY KEY,
    source_file    VARCHAR(255)  NULL,
    account_number VARCHAR(50)   NULL,
    original_date  DATE          NOT NULL,
    description    VARCHAR(255)  NOT NULL,
    doc_number     VARCHAR(20)   NULL,
    amount         NUMERIC(10,2) NOT NULL,
    balance        NUMERIC(14,2) NULL,
    type           VARCHAR(3)    NOT NULL,
    is_processed   BOOLEAN       NOT NULL DEFAULT FALSE,
    assigned_category_id BIGINT REFERENCES finance_category(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS finance_staging_transaction_is_processed_idx   ON finance_staging_transaction (is_processed);
CREATE INDEX IF NOT EXISTS finance_staging_transaction_original_date_idx  ON finance_staging_transaction (original_date);
CREATE INDEX IF NOT EXISTS finance_staging_transaction_category_id_idx    ON finance_staging_transaction (assigned_category_id);

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
    type             VARCHAR(3)   NOT NULL,
    is_processed     BOOLEAN      NOT NULL DEFAULT FALSE,
    assigned_category_id BIGINT REFERENCES finance_category(id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS finance_staging_cc_is_processed_idx    ON finance_staging_cc_transaction (is_processed);
CREATE INDEX IF NOT EXISTS finance_staging_cc_original_date_idx   ON finance_staging_cc_transaction (original_date);
CREATE INDEX IF NOT EXISTS finance_staging_cc_category_id_idx     ON finance_staging_cc_transaction (assigned_category_id);
