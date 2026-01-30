BEGIN TRANSACTION;

INSERT INTO approved_funding (
    symbol, usdt_amount, next_funding_at, active, executed, created_at, updated_at, version
) VALUES (
             'SOL/USDT',
             200.0,
             (CAST(strftime('%s','now') AS INTEGER) * 1000) + (30 * 1000),  -- +60 сек
             1,
             0,
             (CAST(strftime('%s','now') AS INTEGER) * 1000),
             (CAST(strftime('%s','now') AS INTEGER) * 1000),
             0
         );


INSERT INTO approved_funding_exchange (funding_id, exchange)
VALUES (last_insert_rowid(), 'BYBIT');

COMMIT;

SELECT
    strftime('%Y-%m-%d %H:%M:%S', next_funding_at / 1000, 'unixepoch', 'localtime') AS next_funding_at_local
FROM approved_funding where active = true and executed = false;


