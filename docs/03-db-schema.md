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

## Invariants
- next_funding_at хранится только как epoch millis UTC
- exchanges для funding должны быть non-empty (иначе исполнение пропускается)
- SQLite файл по умолчанию: `./data/fundingarb.db` (в Docker — `/data/fundingarb.db`), поэтому не хранить его в репозитории.
