BEGIN TRANSACTION;

INSERT INTO approved_funding (symbol, usdt_amount, next_funding_at, active, executed, created_at, updated_at, version)
VALUES ('ATOM/USDT',
        200.0,
        (CAST(strftime('%s', 'now') AS INTEGER) * 1000) + (5 * 1000), -- +60 сек
        1,
        0,
        (CAST(strftime('%s', 'now') AS INTEGER) * 1000),
        (CAST(strftime('%s', 'now') AS INTEGER) * 1000),
        0);

INSERT INTO approved_funding_exchange (funding_id, exchange)
VALUES (last_insert_rowid(), 'BINANCE');

COMMIT;

select symbol, active, executed,
       strftime('%Y-%m-%d %H:%M:%S%MS', created_at / 1000, 'unixepoch', 'localtime')           AS 'Created',
       strftime('%Y-%m-%d %H:%M:%S%MS', executed_at / 1000, 'unixepoch', 'localtime') AS 'Executed',
       strftime('%Y-%m-%d %H:%M:%S%MS', next_funding_at / 1000, 'unixepoch', 'localtime')   AS 'Funding at'
 from approved_funding order by reverse(approved_funding.created_at) limit 1 ;



select order_id,
       strftime('%Y-%m-%d %H:%M:%S%MS', created_at / 1000, 'unixepoch', 'localtime')           AS 'Created',
       strftime('%Y-%m-%d %H:%M:%S%MS', exchange_executed_at / 1000, 'unixepoch', 'localtime') AS 'Executed on Exchange ',
       strftime('%Y-%m-%d %H:%M:%S%MS', server_received_at / 1000, 'unixepoch', 'localtime')   AS 'Server received',
       strftime('%Y-%m-%d %H:%M:%S%MS', funding_at / 1000, 'unixepoch', 'localtime')           AS 'Funding at',
       exchange,
       symbol,
       timestamp_source
from order_execution_time;

