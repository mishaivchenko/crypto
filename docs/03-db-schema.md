# DB Schema

## Tables

### approved_funding
- id (PK)
- symbol (TEXT)
- usdt_amount (NUMERIC)
- next_funding_at (INTEGER epoch millis UTC)
- active (BOOLEAN)
- executed (BOOLEAN)
- created_at (INTEGER epoch millis)
- updated_at (INTEGER epoch millis)
- version (INTEGER) optimistic lock

### approved_funding_exchange
- funding_id (FK -> approved_funding.id)
- exchange (TEXT)

### funding_event
- id (PK)
- venue (TEXT)
- symbol (TEXT)
- funding_time (INTEGER epoch millis UTC)
- funding_rate_pct (NUMERIC, nullable)
- status (TEXT)
- source_type (TEXT, nullable)
- source_ref (TEXT, nullable)
- discovered_at (INTEGER epoch millis UTC)
- created_at (INTEGER epoch millis UTC)
- updated_at (INTEGER epoch millis UTC)
- version (INTEGER optimistic lock)

### armed_trade
- id (PK)
- funding_event_id (FK-like reference by id)
- notional_usd (NUMERIC)
- intended_side (TEXT, nullable)
- planned_entry_at (INTEGER epoch millis UTC, nullable)
- planned_exit_at (INTEGER epoch millis UTC, nullable)
- state (TEXT)
- notes (TEXT, nullable)
- created_at / updated_at / version

### order_attempt
- id (PK)
- armed_trade_id
- venue
- symbol
- side
- execution_type
- quantity
- limit_price (nullable)
- status
- external_order_id (nullable)
- submitted_at (nullable)
- exchange_timestamp (nullable)
- failure_reason (nullable)
- created_at / updated_at / version

### trade_position
- id (PK)
- armed_trade_id
- venue
- symbol
- side
- quantity
- entry_price (nullable)
- exit_price (nullable)
- state
- opened_at (nullable)
- closed_at (nullable)
- created_at / updated_at / version

### trade_outcome
- id (PK)
- armed_trade_id
- gross_pnl_usd (nullable)
- net_pnl_usd (nullable)
- fees_usd (nullable)
- outcome_code
- notes (nullable)
- evaluated_at
- created_at / updated_at / version

### venue_timing_profile
- id (PK)
- venue
- symbol
- observed_lag_ms (nullable)
- entry_latency_ms (nullable)
- exit_latency_ms (nullable)
- sampled_at
- notes (nullable)
- created_at / updated_at / version

## Invariants
- next_funding_at хранится только как epoch millis UTC
- legacy `approved_funding*` остаются читаемыми, но не считаются будущей core model
- новый домен хранится отдельно от legacy approval-модели
- `trade_position` используется вместо логического имени `position`, чтобы не опираться на потенциально конфликтное SQL-ключевое слово
- SQLite файл по умолчанию: `./data/fundingarb.db` (в Docker — `/data/fundingarb.db`), поэтому не хранить его в репозитории.
