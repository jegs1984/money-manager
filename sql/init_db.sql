-- Initialize finance database schema

-- BudgetMonth table
CREATE TABLE finance_budget_month (
    id BIGSERIAL PRIMARY KEY,
    month_date DATE NOT NULL UNIQUE,
    is_active BOOLEAN DEFAULT true NOT NULL
);

CREATE INDEX idx_finance_budget_month_month_date ON finance_budget_month(month_date);

-- Category table
CREATE TABLE finance_category (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    "group" VARCHAR(100) NOT NULL
);

CREATE INDEX idx_finance_category_name ON finance_category(name);

-- BudgetItem table
CREATE TABLE finance_budget_item (
    id BIGSERIAL PRIMARY KEY,
    budget_month_id BIGINT NOT NULL REFERENCES finance_budget_month(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES finance_category(id) ON DELETE PROTECT,
    type VARCHAR(3) NOT NULL,
    projected_amount NUMERIC(10, 2) DEFAULT 0.00 NOT NULL,
    CONSTRAINT finance_budget_item_projected_amount_non_negative CHECK (projected_amount >= 0.00),
    UNIQUE(budget_month_id, category_id)
);

CREATE INDEX idx_finance_budget_item_budget_month_id ON finance_budget_item(budget_month_id);
CREATE INDEX idx_finance_budget_item_category_id ON finance_budget_item(category_id);

-- Transaction table
CREATE TABLE finance_transaction (
    id BIGSERIAL PRIMARY KEY,
    budget_item_id BIGINT REFERENCES finance_budget_item(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    real_amount NUMERIC(10, 2) NOT NULL,
    description VARCHAR(255) NOT NULL,
    notes TEXT
);

CREATE INDEX idx_finance_transaction_date ON finance_transaction(date);
CREATE INDEX idx_finance_transaction_budget_item_id ON finance_transaction(budget_item_id);

-- StagingTransaction table (Reconciliation staging model)
CREATE TABLE finance_staging_transaction (
    id BIGSERIAL PRIMARY KEY,
    original_date DATE NOT NULL,
    description VARCHAR(255) NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    type VARCHAR(3) NOT NULL,
    is_processed BOOLEAN DEFAULT false NOT NULL,
    assigned_category_id BIGINT REFERENCES finance_category(id) ON DELETE SET NULL
);

CREATE INDEX idx_finance_staging_transaction_is_processed ON finance_staging_transaction(is_processed);
CREATE INDEX idx_finance_staging_transaction_assigned_category_id ON finance_staging_transaction(assigned_category_id);
