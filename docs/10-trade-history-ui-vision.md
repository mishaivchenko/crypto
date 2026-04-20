# Trade History UI Vision

## Product Idea

История сделок должна быть не таблицей “всё подряд”, а инструментом разбора: почему сделка была подготовлена, что engine планировал, какие попытки входа были сделаны, какая latency была фактической и поймали ли мы funding movement.

Главная мысль UI:

`Signal -> Decision -> Plan -> Attempts -> Position -> Outcome`

Оператор должен за 10 секунд понять, была ли сделка хорошей, плохой или технически испорченной.

## Main Screen: Trade History

Основной экран должен состоять из трёх зон:

- left rail filters.
- central trade timeline/list.
- right side inspector.

## List Item

Каждая запись в списке:

- symbol и venue.
- funding time.
- trade state.
- outcome code.
- net PnL, если уже рассчитан.
- planned entry vs actual first submit.
- effective latency.
- attempts count.
- small health badge: `clean`, `late`, `partial`, `failed`, `manual override`.

List item не должен выглядеть как generic card. Лучше формат “операторская строка”:

```text
WET/USDT   gate   funding 00:00:00   SHORT   3 attempts / 150ms   +12ms late   CLOSED   +$4.12
```

## Filters

Нужны быстрые фильтры:

- venue.
- symbol.
- date range.
- state.
- outcome.
- latency bucket.
- attempt count.
- source type.
- only failed.
- only profitable.
- only manually adjusted.

## Detail Inspector

Detail view должен быть story-based:

### 1. Source

- source type.
- raw symbol.
- normalized symbol.
- candidate id.
- detection time.
- review note.

### 2. Event

- venue.
- funding time.
- funding rate.
- time from detection to approval.
- time from approval to arm.

### 3. Plan

- planned entry.
- planned exit.
- entry lead.
- exit lead.
- entry attempt count.
- spacing.
- measured latency.
- manual adjustment.
- effective trigger lead.

### 4. Attempts

Таблица попыток:

- attempt number.
- target time.
- trigger time.
- submitted at.
- acknowledged at.
- filled at.
- status.
- exchange order id.
- exchange error.
- submit latency.
- ack latency.
- fill latency.

Визуально это лучше показывать как horizontal micro-timeline, а не только таблицу.

### 5. Position

- opened at.
- entry price.
- quantity.
- closing started at.
- exit price.
- closed at.
- fees.

### 6. Outcome

- gross PnL.
- net PnL.
- slippage.
- capture quality.
- outcome code.
- notes.

## Visual Language

Стиль должен быть ближе к trading operations console, но без fake terminal эстетики.

Рекомендации:

- плотная сетка, но много воздуха вокруг critical numbers.
- моноширинные цифры для latency/time/PnL.
- разные визуальные формы для `Candidate`, `Event`, `Plan`, `Attempt`, `Outcome`.
- timeline как главная метафора.
- цвет использовать только для статуса и риска, не для декора.

## Important Widgets

### Latency Strip

Мини-граф:

```text
planned trigger | submitted | ack | filled
```

Показывает, где потеряли миллисекунды.

### Attempt Ladder

Вертикальный или горизонтальный ladder:

```text
#1 target 00:00.000 -> filled
#2 target 00:00.150 -> rejected
#3 target 00:00.300 -> cancelled
```

### Decision Diff

Показывает, что оператор изменил вручную:

- venue override.
- funding time override.
- manual latency adjustment.
- notional.
- entry attempts.
- spacing.

### Replay Mode

Не для MVP, но очень сильная идея: replay сделки по времени, чтобы увидеть sequence событий в 1x/5x/20x.

## MVP Version Of This UI

Минимально достаточно:

- `/history` или tab `History`.
- список `ArmedTrade` + joined `FundingEvent`.
- detail drawer.
- journal timeline.
- latency block.
- attempt plan block.
- execution attempts block from `OrderAttempt`.

## Implemented First Slice

В monitor UI добавлен tab `Trade History`.

Реализовано:

- фильтры по symbol, venue, state, health, funding date range, failed/manual-only;
- операторская строка trade history вместо generic cards;
- health badge: `clean`, `burst plan`, `latency watch`, `manual override`, `failed`, `cancelled`;
- detail drawer по story sequence `Source -> Event -> Plan -> Attempts -> Position -> Outcome`;
- attempt ladder строится из `entryAttemptCount`, `entrySpacingMs`, `plannedEntryAt`, `effectiveEntryLatencyMs`;
- latency strip показывает planned trigger и честные pending stages для submitted/ack/filled;
- journal timeline подключён к существующему `ArmedTrade` journal endpoint;
- frontend view-model покрыт `node:test` и подключён к Gradle `monitor-app:check`.

Пока не реализовано:

- фактические ack/filled timestamps;
- position snapshot;
- outcome/PnL/capture quality;
- replay mode.

## Why This Matters

История сделок станет главным инструментом обучения системы. Если мы не видим, почему сделка была успешной или провальной, мы не сможем улучшать latency, timing и venue selection.

Хорошая history UI должна отвечать на вопросы:

- Мы вошли вовремя?
- Какая latency была реальной?
- Ручная поправка помогла или ухудшила?
- Какой attempt поймал движение?
- Venue был проблемой или идея была плохой?
- Что нужно изменить перед следующей сделкой?
