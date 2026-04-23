CREATE TABLE armed_trade (
    entry_attempt_count INTEGER,
    notional_usd NUMERIC(19,8) NOT NULL,
    armed_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    effective_entry_latency_ms BIGINT,
    entry_lead_ms BIGINT,
    entry_spacing_ms BIGINT,
    event_age_ms_at_arm BIGINT,
    exit_lead_ms BIGINT,
    funding_event_id BIGINT NOT NULL,
    id INTEGER,
    manual_latency_adjustment_ms BIGINT,
    measured_entry_latency_ms BIGINT,
    planned_entry_at BIGINT,
    planned_exit_at BIGINT,
    updated_at BIGINT NOT NULL,
    version BIGINT,
    notes VARCHAR(1000),
    arm_source VARCHAR(255) CHECK (arm_source IN ('EVENT_API', 'DIRECT_TRADE_API')),
    intended_side VARCHAR(255) CHECK (intended_side IN ('LONG', 'SHORT')),
    state VARCHAR(255) NOT NULL CHECK (
        state IN ('ARMED', 'ENTRY_PENDING', 'ENTRY_ATTEMPTED', 'OPEN', 'EXIT_PENDING', 'CLOSED', 'CANCELLED', 'FAILED')
    ),
    PRIMARY KEY (id)
);

CREATE TABLE funding_event (
    funding_rate_pct NUMERIC(19,8),
    created_at BIGINT NOT NULL,
    discovered_at BIGINT NOT NULL,
    funding_time BIGINT NOT NULL,
    id INTEGER,
    signal_candidate_id BIGINT,
    updated_at BIGINT NOT NULL,
    version BIGINT,
    source_ref VARCHAR(255),
    source_type VARCHAR(255),
    status VARCHAR(255) NOT NULL CHECK (status IN ('DISCOVERED', 'ARMED', 'EXPIRED', 'CANCELLED')),
    symbol VARCHAR(255) NOT NULL,
    venue VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE instrument_metadata (
    min_notional_value NUMERIC(19,8),
    min_order_qty NUMERIC(19,8),
    qty_step NUMERIC(19,8),
    quantity_precision INTEGER,
    created_at BIGINT NOT NULL,
    id INTEGER,
    last_synced_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    version BIGINT,
    base_asset VARCHAR(255),
    canonical_symbol VARCHAR(255) NOT NULL,
    instrument_type VARCHAR(255),
    quote_asset VARCHAR(255),
    status VARCHAR(255) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'UNSUPPORTED')),
    venue VARCHAR(255) NOT NULL,
    venue_symbol VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE operator_account (
    enabled BOOLEAN NOT NULL,
    created_at BIGINT NOT NULL,
    id INTEGER,
    updated_at BIGINT NOT NULL,
    version BIGINT,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL UNIQUE,
    PRIMARY KEY (id)
);

CREATE TABLE operator_exchange_credential (
    last_connection_http_status INTEGER,
    created_at BIGINT NOT NULL,
    id INTEGER,
    last_checked_at BIGINT,
    operator_id BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    version BIGINT,
    api_key_ciphertext TEXT,
    api_key_mask VARCHAR(255),
    connection_message TEXT,
    connection_status VARCHAR(255) NOT NULL CHECK (
        connection_status IN ('NOT_CONNECTED', 'CONNECTED', 'INVALID_CREDENTIALS', 'ERROR', 'UNSUPPORTED')
    ),
    mode VARCHAR(255) NOT NULL CHECK (mode IN ('TESTNET', 'PRODUCTION')),
    passphrase_ciphertext TEXT,
    passphrase_mask VARCHAR(255),
    secret_key_ciphertext TEXT,
    secret_key_mask VARCHAR(255),
    venue VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE order_attempt (
    attempt_number INTEGER,
    limit_price NUMERIC(19,8),
    quantity NUMERIC(19,8) NOT NULL,
    armed_trade_id BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    exchange_timestamp BIGINT,
    id INTEGER,
    submitted_at BIGINT,
    target_entry_at BIGINT,
    trigger_at BIGINT,
    updated_at BIGINT NOT NULL,
    version BIGINT,
    attempt_key VARCHAR(240),
    failure_reason VARCHAR(1000),
    execution_type VARCHAR(255) NOT NULL CHECK (execution_type IN ('MARKET', 'LIMIT')),
    external_order_id VARCHAR(255),
    side VARCHAR(255) NOT NULL CHECK (side IN ('LONG', 'SHORT')),
    status VARCHAR(255) NOT NULL CHECK (
        status IN ('CREATED', 'SUBMITTED', 'ACKNOWLEDGED', 'FILLED', 'CANCELLED', 'REJECTED', 'FAILED', 'EXPIRED')
    ),
    symbol VARCHAR(255) NOT NULL,
    venue VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE signal_candidate (
    source_funding_rate_pct NUMERIC(18,8),
    created_at BIGINT NOT NULL,
    detected_at BIGINT NOT NULL,
    funding_event_id BIGINT,
    id INTEGER,
    reviewed_at BIGINT,
    source_chat_id BIGINT,
    source_funding_time BIGINT,
    source_message_id BIGINT,
    updated_at BIGINT NOT NULL,
    version BIGINT,
    normalization_failure_reason TEXT,
    normalized_symbol VARCHAR(255),
    raw_payload TEXT,
    raw_symbol VARCHAR(255) NOT NULL,
    review_decision VARCHAR(255) CHECK (review_decision IN ('APPROVE', 'REJECT')),
    review_notes TEXT,
    source_type VARCHAR(255) NOT NULL,
    source_venue VARCHAR(255),
    status VARCHAR(255) NOT NULL CHECK (
        status IN ('NEW', 'NORMALIZED', 'REJECTED', 'APPROVED', 'EVENT_CREATED', 'FAILED', 'DELETED')
    ),
    venue_hints VARCHAR(255),
    PRIMARY KEY (id)
);

CREATE TABLE trade_journal (
    created_at BIGINT NOT NULL,
    entity_id BIGINT NOT NULL,
    id INTEGER,
    updated_at BIGINT NOT NULL,
    version BIGINT,
    note VARCHAR(2000),
    actor_ref VARCHAR(255),
    actor_type VARCHAR(255) NOT NULL CHECK (actor_type IN ('SYSTEM', 'OPERATOR')),
    entity_type VARCHAR(255) NOT NULL CHECK (
        entity_type IN ('SIGNAL_CANDIDATE', 'FUNDING_EVENT', 'ARMED_TRADE')
    ),
    event_code VARCHAR(255) NOT NULL CHECK (
        event_code IN (
            'CANDIDATE_APPROVED',
            'CANDIDATE_REJECTED',
            'CANDIDATE_DELETED',
            'FUNDING_EVENT_CREATED',
            'FUNDING_EVENT_ARMED',
            'ARMED_TRADE_CREATED'
        )
    ),
    new_state VARCHAR(255),
    old_state VARCHAR(255),
    PRIMARY KEY (id)
);

CREATE TABLE trade_outcome (
    fees_usd NUMERIC(19,8),
    gross_pnl_usd NUMERIC(19,8),
    net_pnl_usd NUMERIC(19,8),
    armed_trade_id BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    evaluated_at BIGINT NOT NULL,
    id INTEGER,
    updated_at BIGINT NOT NULL,
    version BIGINT,
    notes VARCHAR(1000),
    outcome_code VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE trade_position (
    entry_price NUMERIC(19,8),
    exit_price NUMERIC(19,8),
    quantity NUMERIC(19,8) NOT NULL,
    armed_trade_id BIGINT NOT NULL,
    closed_at BIGINT,
    created_at BIGINT NOT NULL,
    id INTEGER,
    opened_at BIGINT,
    updated_at BIGINT NOT NULL,
    version BIGINT,
    side VARCHAR(255) NOT NULL CHECK (side IN ('LONG', 'SHORT')),
    state VARCHAR(255) NOT NULL CHECK (state IN ('PENDING_OPEN', 'OPEN', 'CLOSING', 'CLOSED', 'FAILED')),
    symbol VARCHAR(255) NOT NULL,
    venue VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE venue_profile (
    last_connection_http_status INTEGER,
    last_checked_at BIGINT,
    connection_message TEXT,
    connection_status VARCHAR(255) NOT NULL CHECK (
        connection_status IN ('NOT_CONNECTED', 'CONNECTED', 'INVALID_CREDENTIALS', 'ERROR', 'UNSUPPORTED')
    ),
    selected_mode VARCHAR(255) NOT NULL CHECK (selected_mode IN ('TESTNET', 'PRODUCTION')),
    venue VARCHAR(255) NOT NULL,
    PRIMARY KEY (venue)
);

CREATE TABLE venue_timing_profile (
    created_at BIGINT NOT NULL,
    entry_latency_ms BIGINT,
    exit_latency_ms BIGINT,
    id INTEGER,
    observed_lag_ms BIGINT,
    sampled_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    version BIGINT,
    notes VARCHAR(1000),
    symbol VARCHAR(255) NOT NULL,
    venue VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_armed_trade_event_id ON armed_trade (funding_event_id);
CREATE INDEX idx_armed_trade_state ON armed_trade (state);
CREATE INDEX idx_funding_event_venue_symbol ON funding_event (venue, symbol);
CREATE INDEX idx_funding_event_funding_time ON funding_event (funding_time);
CREATE UNIQUE INDEX idx_instrument_metadata_venue_symbol ON instrument_metadata (venue, canonical_symbol);
CREATE INDEX idx_instrument_metadata_status ON instrument_metadata (status);
CREATE UNIQUE INDEX idx_operator_credential_unique ON operator_exchange_credential (operator_id, venue, mode);
CREATE INDEX idx_order_attempt_trade_id ON order_attempt (armed_trade_id);
CREATE UNIQUE INDEX idx_order_attempt_key ON order_attempt (attempt_key);
CREATE INDEX idx_order_attempt_status ON order_attempt (status);
CREATE INDEX idx_signal_candidate_detected_at ON signal_candidate (detected_at);
CREATE INDEX idx_signal_candidate_status ON signal_candidate (status);
CREATE INDEX idx_signal_candidate_source_msg ON signal_candidate (source_type, source_chat_id, source_message_id);
CREATE INDEX idx_signal_candidate_raw_detected ON signal_candidate (source_type, raw_symbol, detected_at);
CREATE INDEX idx_trade_journal_entity ON trade_journal (entity_type, entity_id);
CREATE INDEX idx_trade_journal_event_code ON trade_journal (event_code);
CREATE INDEX idx_trade_outcome_trade_id ON trade_outcome (armed_trade_id);
CREATE INDEX idx_trade_position_trade_id ON trade_position (armed_trade_id);
CREATE INDEX idx_trade_position_state ON trade_position (state);
CREATE INDEX idx_venue_timing_profile_venue_symbol ON venue_timing_profile (venue, symbol);
