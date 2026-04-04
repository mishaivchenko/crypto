# Funding Candidate Source

## Что изменилось
- TDLib полностью убран из runtime candidate ingestion.
- Новый источник кандидатов:
  - `https://uainvest.com.ua/api/funding?sort_by=funding&sort_dir=asc&limit=30`
- Сервис poll-ит внешний funding API, а не Telegram-канал.

## Что делает источник
- получает funding entries по нескольким биржам;
- фильтрует их по enabled venues;
- нормализует символ через instrument registry;
- обновляет `FundingWatchlistService`;
- создаёт `SignalCandidate` через существующий application flow.

## Дедупликация
- один funding window для одного символа дедуплицируется synthetic source id;
- следующий funding cycle для того же символа создаёт новый candidate.

## Telegram
- Telegram bot может остаться включённым как operator/diagnostic интерфейс;
- но кандидатный поток и наблюдаемое состояние больше не зависят от TDLib.
