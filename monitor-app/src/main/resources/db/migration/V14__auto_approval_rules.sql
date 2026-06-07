CREATE TABLE IF NOT EXISTS auto_approval_rule (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            VARCHAR(100) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    mode            VARCHAR(20) NOT NULL DEFAULT 'BOTH' CHECK (mode IN ('TESTNET', 'PRODUCTION', 'BOTH')),
    min_funding_rate_pct    NUMERIC(10, 6),
    max_funding_rate_pct    NUMERIC(10, 6),
    allowed_venues          TEXT,
    allowed_ai_recommendations TEXT,
    min_ai_confidence       NUMERIC(4, 3),
    allowed_liquidity_scores TEXT,
    default_notional_usd    NUMERIC(18, 2) NOT NULL DEFAULT 10.00,
    default_side            VARCHAR(10) NOT NULL DEFAULT 'SHORT' CHECK (default_side IN ('LONG', 'SHORT')),
    action                  VARCHAR(20) NOT NULL DEFAULT 'AUTO_EXECUTE' CHECK (action IN ('AUTO_EXECUTE', 'AUTO_REJECT')),
    priority                INTEGER NOT NULL DEFAULT 100,
    notes                   TEXT,
    created_at              BIGINT NOT NULL,
    updated_at              BIGINT NOT NULL,
    version                 BIGINT
);

CREATE INDEX IF NOT EXISTS idx_auto_approval_rule_enabled_priority ON auto_approval_rule (enabled, priority);
