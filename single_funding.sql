BEGIN TRANSACTION;

INSERT INTO approved_funding (
    symbol, usdt_amount, next_funding_at, active, executed, created_at, updated_at, version
) VALUES (
             'SOL/USDT',
             50.0,
             (CAST(strftime('%s','now') AS INTEGER) * 1000) + (2 * 60 * 1000),  -- +60 сек
             1,
             0,
             (CAST(strftime('%s','now') AS INTEGER) * 1000),
             (CAST(strftime('%s','now') AS INTEGER) * 1000),
             0
         );

INSERT INTO approved_funding_exchange (funding_id, exchange)
VALUES (last_insert_rowid(), 'BINANCE');

INSERT INTO approved_funding (
    symbol, usdt_amount, next_funding_at, active, executed, created_at, updated_at, version
) VALUES (
             'ARB/USDT',
             50.0,
             (CAST(strftime('%s','now') AS INTEGER) * 1000) + (2 * 60 * 1000),  -- +60 сек
             1,
             0,
             (CAST(strftime('%s','now') AS INTEGER) * 1000),
             (CAST(strftime('%s','now') AS INTEGER) * 1000),
             0
         );


INSERT INTO approved_funding_exchange (funding_id, exchange)
VALUES (last_insert_rowid(), 'BYBIT');

COMMIT;
