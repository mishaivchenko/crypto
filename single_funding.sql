BEGIN TRANSACTION;

INSERT INTO approved_funding (
    symbol, usdt_amount, next_funding_at, active, executed, created_at, updated_at, version
) VALUES (
             'SOL/USDT',
             200.0,
             (CAST(strftime('%s','now') AS INTEGER) * 1000) + (5 * 1000),  -- +60 сек
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
FROM approved_funding join order_execution_time where active = true and executed = false;


select
    strftime('%Y-%m-%d %H:%M:%S', created_at / 1000, 'unixepoch', 'localtime') AS 'Created',
    strftime('%Y-%m-%d %H:%M:%S', exchange_executed_at / 1000, 'unixepoch', 'localtime') AS 'Executed on Exchange ',
    strftime('%Y-%m-%d %H:%M:%S', server_received_at / 1000, 'unixepoch', 'localtime') AS 'Server received',
    strftime('%Y-%m-%d %H:%M:%S', funding_at / 1000, 'unixepoch', 'localtime') AS 'Funding at',
    exchange, symbol, timestamp_source

from order_execution_time;

