# Phase 0-1 Implementation Brief

## Purpose of This Document

Этот документ нужен как полное описание Phase 0-1 в ветке `feature/phase0-phase1-foundation`.

Он предназначен не как короткий changelog, а как полноценный контекстный материал для дальнейшего изучения в NotebookLM:
- что было до изменений;
- почему текущий код нельзя было считать правильной основой;
- что именно было реализовано;
- зачем это было сделано;
- что изменилось архитектурно;
- что осталось legacy;
- что сознательно не реализовывалось;
- какие следующие шаги логично делать дальше.

Если коротко: Phase 0-1 не строит торговый движок. Эта фаза делает кодовую базу безопасной, честной и пригодной для дальнейшей миграции к funding-event trading системе.

---

## 1. Business Context and Why This Phase Was Needed

Изначальная бизнес-цель проекта не в том, чтобы "собирать funding", и не в том, чтобы просто одобрять символ и ставить BUY перед funding timestamp.

Реальная цель системы:
- отслеживать funding-related события;
- понимать короткое окно движения цены вокруг funding event;
- со временем научиться открывать и закрывать позиции в узком временном окне;
- делать это с учётом venue-specific timing, execution quirks и измерения результата.

На момент начала takeover кодовая база была ближе к следующей модели:
- Telegram signal intake;
- in-memory watchlists;
- ручной approval funding-кандидата;
- запись одного `nextFundingAt`;
- scheduler около этого времени;
- exchange order scaffold, местами с опасной и вводящей в заблуждение семантикой.

Это создавало сразу несколько проблем:
- центральная доменная модель была неверной;
- legacy execution path мог быть опасным;
- терминология и названия маскировали реальные риски;
- Telegram оказался слишком близко к execution semantics;
- проект выглядел более готовым к trading, чем был на самом деле.

Phase 0-1 была нужна для того, чтобы:
- сделать runtime безопасным по умолчанию;
- перестать выдавать legacy funding-flow за будущую архитектуру;
- ввести новый доменный скелет прямо в коде;
- сохранить работоспособный Telegram ingest;
- подготовить опорную структуру для следующей фазы.

---

## 2. What the Codebase Was Before This Branch

До этой ветки проект можно было описать как transitionary funding bot prototype с опасно смешанными обязанностями.

### 2.1 Core behavior before Phase 0-1

Основная последовательность выглядела так:
1. Telegram signal попадал в TDLib pipeline.
2. Символы обновляли funding/watchlist состояние в памяти.
3. Оператор мог руками approve funding-кандидата.
4. Сущность `ApprovedFundingEntity` сохранялась в SQLite.
5. Scheduler выбирал ближайший `nextFundingAt`.
6. Execution path пытался отправлять legacy order flow.

### 2.2 What was technically present

В коде уже существовали:
- Spring Boot монолит;
- SQLite persistence;
- Telegram bot и TDLib ingestion;
- watchlists;
- legacy scheduler;
- несколько exchange adapters;
- базовые REST endpoints;
- часть тестов.

### 2.3 What was wrong directionally

Основные directional-проблемы были такими:
- `ApprovedFundingEntity` фактически выступал за главный агрегат, хотя не подходит под реальную доменную модель;
- scheduler и executor были построены вокруг legacy funding approval semantics;
- существовал misleading execution flow с "test order" terminology;
- Gate path не должен был оставаться потенциально исполняемым по умолчанию;
- Telegram bot слишком сильно напоминал control plane, хотя должен остаться ingress/ops-слоем;
- новый домен вообще отсутствовал в коде как first-class модель.

Именно поэтому Phase 0-1 была не "продолжением старого бота", а controlled foundation + domain correction.

---

## 3. What Phase 0-1 Was Supposed to Achieve

Phase 0-1 не должна была строить полноценный MVP trading engine.

Её целевые результаты были другими:

### 3.1 Safety
- запуск по умолчанию должен быть безопасным;
- accidental live execution должен быть закрыт;
- broken or misleading execution paths должны быть явно ограничены.

### 3.2 Honesty
- README и docs должны честно описывать текущее состояние;
- legacy flow должен быть обозначен как legacy;
- новый инженер не должен читать репозиторий и думать, что это уже почти готовая funding-event execution system.

### 3.3 Domain correction
- новый доменный скелет должен существовать в коде;
- домен должен быть отделён от Telegram и legacy approval semantics;
- новый control entry должен начинаться с внутреннего REST API и application services.

### 3.4 Controlled continuity
- Telegram signal processing нельзя ломать;
- legacy код нельзя просто удалить без подготовки;
- build/test stability нужно сохранить.

---

## 4. High-Level Result of This Branch

После Phase 0-1 репозиторий стал другим по смыслу.

Теперь он представляет собой:
- Spring Boot монолит, который находится в controlled rewrite phase;
- safe-by-default runtime;
- работающий Telegram ingest;
- legacy funding flow, оставленный только как transitional code path;
- новый доменный скелет funding-event trading;
- новый persistence direction;
- новый internal REST API как первый правильный вход в новый домен.

Это всё ещё не production trading engine.
Но это уже и не "старый бот, который случайно умеет ставить ордера".

---

## 5. Before vs After

## Before
- доменная логика была привязана к `ApprovedFundingEntity`;
- execution semantics были смешаны с legacy approval flow;
- Telegram bot выглядел как часть control plane;
- runtime по умолчанию не был достаточно fail-closed;
- нового домена не существовало;
- persistence отражала прошлую модель, а не целевую;
- API нового мира отсутствовал.

## After
- введён явный safety model;
- legacy execution guard централизован;
- `gate` заблокирован в legacy executable path по умолчанию;
- Telegram ingest сохранён, но Telegram bot переведён в ingest/diagnostic роль;
- новый домен появился в коде как first-class core skeleton;
- добавлена новая persistence-модель для будущего ядра;
- появился новый internal REST API `/api/v1/**`;
- docs больше не притворяются, что проект уже является полноценной trading system.

---

## 6. Safety Model Introduced in Phase 0-1

Одним из важнейших результатов фазы стал явный safety model для execution.

### 6.1 Execution mode

Добавлена модель:
- `DISABLED`
- `SHADOW`
- `LIVE`

### 6.2 Default runtime behavior

По умолчанию:
- `trading.execution.mode=DISABLED`
- `trading.execution.legacy-enabled=false`
- `trading.execution.blocked-venues=gate`

Это означает:
- старый код не должен случайно торговать;
- broken legacy venues не должны "вдруг" оказаться executable;
- локальный и тестовый запуск безопасны.

### 6.3 Legacy execution guard

Введён единый guard-слой для legacy execution entry points.

Он теперь отвечает за решение:
- может ли legacy flow вообще исполняться;
- допустим ли конкретный venue;
- разрешён ли live execution;
- должна ли операция быть заблокирована немедленно.

Важно, что блокировка теперь не является "тихим пропуском" с опасными side effects.

### 6.4 Passive legacy scheduler behavior

Legacy scheduler сохранён, но теперь в safe-mode он должен вести себя как passive detector/logger.

Это принципиально важно: Phase 0-1 не убирает весь legacy flow, но меняет его operational semantics.

---

## 7. Why Telegram Ingest Was Preserved

Одно из ключевых решений: Telegram signals продолжают обрабатываться.

Причина проста:
- на текущем этапе Telegram остаётся входом для candidate observation;
- TDLib pipeline уже существует;
- бизнес пока ещё реально использует Telegram signals как источник первичного кандидата;
- удалять или ломать этот слой было бы вредно для прогресса.

При этом было важно изменить роль Telegram в архитектуре.

### 7.1 Telegram after Phase 0-1

Telegram теперь трактуется так:
- ingest adapter;
- источник candidate-level информации;
- diagnostic/visibility слой;
- не control plane нового торгового домена.

### 7.2 What specifically changed

Bot и Telegram-related flow были сохранены, но архитектурно переосмыслены:
- signal parsing и watchlist refresh остались рабочими;
- новые core actions не были добавлены в bot;
- bot больше не должен восприниматься как правильная точка входа в новый trading core;
- legacy trading control semantics в bot были намеренно ограничены.

Именно это позволяет одновременно:
- сохранить текущий рабочий ingest;
- не загрязнять новый домен Telegram-абстракциями.

---

## 8. Legacy Isolation: What Stayed and What Changed

Phase 0-1 не переписывала весь старый код.
Но старый код перестал быть "неявным будущим".

### 8.1 Legacy parts explicitly treated as legacy

Старый мир теперь должен читаться именно как legacy/transitional:
- `ApprovedFundingEntity`
- funding approval flow
- legacy scheduler
- legacy order execution
- legacy test-order semantics

### 8.2 Why we did not delete them

Полное удаление было бы вредным, потому что:
- Telegram/watchlist pipeline ещё опирается на legacy state;
- часть тестов и диагностических сценариев всё ещё используют legacy components;
- rewrite in place требует поэтапной миграции.

### 8.3 What changed in practice

Сделаны три важных вещи:
- legacy flow теперь явно guard'ится;
- новый код больше не строится поверх legacy execution abstractions;
- документация и код больше не описывают legacy approval model как целевую основу.

---

## 9. New Domain Skeleton Introduced

Ключевая часть этой фазы: новый домен появился в коде не как идея в markdown, а как набор реальных типов.

### 9.1 Added domain concepts

Добавлены:
- `FundingEvent`
- `FundingEventStatus`
- `ArmedTrade`
- `ArmedTradeState`
- `OrderIntent`
- `OrderAttempt`
- `OrderAttemptStatus`
- `Position`
- `PositionState`
- `TradeOutcome`
- `TradeSide`
- `ExecutionType`
- `VenueTimingProfile`

### 9.2 Why this matters

До этого репозиторий не имел правильного словаря будущей системы.

Теперь:
- можно описывать будущий trading flow корректными терминами;
- можно строить новые сервисы и persistence поверх правильных сущностей;
- можно перестать навешивать новые смыслы на legacy `ApprovedFundingEntity`.

### 9.3 What this skeleton is and is not

Этот скелет:
- задаёт core vocabulary;
- фиксирует минимальные invariants;
- создаёт основу для следующих итераций.

Он пока не:
- исполняет реальный trade lifecycle end-to-end;
- не рассчитывает strategy logic;
- не является готовым execution engine.

---

## 10. Persistence Direction Introduced

Новый домен не остался абстракцией в памяти. Для него добавлен persistence direction.

### 10.1 What was added

Добавлены JPA entities и repositories для:
- `funding_event`
- `armed_trade`
- `order_attempt`
- `position`
- `trade_outcome`
- `venue_timing_profile`

Также добавлены:
- базовый auditable слой;
- converter для `Instant`;
- mapper'ы между domain model и persistence model.

### 10.2 Why SQLite was kept

SQLite сохранён сознательно:
- чтобы не ломать локальную разработку;
- чтобы не тащить premature migration complexity;
- чтобы Phase 0-1 оставалась foundation-итерацией, а не инфраструктурным переписом.

### 10.3 What was not done here

В этой фазе не делались:
- destructive changes для legacy schema;
- migration tooling через Flyway/Liquibase;
- переход на production-grade external DB.

То есть это именно "правильное направление данных", а не окончательный storage layer.

---

## 11. New Application Layer and Internal API

Чтобы новый домен стал реально usable, были добавлены application services и новый REST API.

### 11.1 Added application services

Появились:
- `FundingEventCommandService`
- `ArmedTradeCommandService`
- `TradeQueryService`

Также введены:
- `DomainValidationException`
- `ResourceNotFoundException`

### 11.2 Added ports

Введены основные порты:
- `FundingEventSourcePort`
- `MarketDataPort`
- `ExecutionPort`
- `OrderStatusPort`
- `SymbolMetadataPort`

Это не полный adapter rewrite.
Смысл этих интерфейсов в том, чтобы:
- провести правильные архитектурные границы;
- подготовить следующую фазу;
- не связывать новый домен с текущими exchange-specific реализациями.

### 11.3 Added internal REST API

Появился первый правильный вход в новый домен:
- `POST /api/v1/funding-events`
- `POST /api/v1/armed-trades`
- `GET /api/v1/armed-trades`
- `GET /api/v1/armed-trades/{id}`

Этот API:
- internal-only;
- DTO-based;
- отделён от JPA entities;
- поддерживается validation и centralized exception handling.

Это очень важный архитектурный сдвиг:
новый control plane начинается не с Telegram bot, а с application service + REST boundary.

---

## 12. Documentation Changes and Why They Matter

Phase 0-1 изменила не только код, но и "правду репозитория".

### 12.1 Docs that were updated

Изменены или добавлены:
- `README.md`
- `docs/00-overview.md`
- `docs/02-runtime-config.md`
- `docs/03-db-schema.md`
- `docs/phase0-phase1-foundation.md`

### 12.2 Why this matters

До этой фазы репозиторий был опасен ещё и тем, что документация создавала неверное представление о степени готовности и направлении.

Теперь docs фиксируют:
- что проект находится в rewrite phase;
- что Telegram ingest сохранён;
- что legacy flow не является будущим ядром;
- что execution по умолчанию safe-by-default;
- что новый доменный фундамент уже введён.

Для takeover это не косметика, а часть engineering correctness.

---

## 13. Tests Added and Updated

Phase 0-1 не могла считаться законченной без тестов на новый safety behavior и новый домен.

### 13.1 New tests

Добавлены тесты на:
- execution guard;
- safety defaults;
- новый persistence слой;
- domain invariants;
- новый REST API;
- Telegram parsing regression.

### 13.2 Why these tests matter

Эти тесты нужны не только для покрытия.
Они фиксируют новый смысл системы:
- legacy execution blocked by default;
- Telegram ingest продолжает работать;
- новый домен реально сохраняется;
- API нового домена работает независимо от Telegram control flow.

---

## 14. What Was Intentionally Not Implemented

Очень важно понимать, что отсутствие некоторых вещей было сознательным.

Phase 0-1 намеренно не включает:
- полноценный funding-event trading engine;
- Gate-first live MVP;
- новый real execution loop;
- adaptive timing logic;
- полноценный risk engine;
- measurement engine для trade outcomes;
- automatic bridge от Telegram signal сразу к `ArmedTrade`;
- production migration framework;
- auth/users/subscription model;
- microservice split.

Это не недоделка фазы.
Это защита от premature complexity.

Цель была не "сделать всё", а подготовить правильную базу под Phase 2+.

---

## 15. File/Module-Level Change Summary

Ниже краткая классификация ключевых изменений по слоям.

### 15.1 Safety and config
- `build.gradle`
- `src/main/resources/application.yml`
- `src/main/java/com/crypto/funding/config/ExecutionMode.java`
- `src/main/java/com/crypto/funding/config/TradingExecutionProperties.java`
- `src/main/java/com/crypto/funding/config/ExecutionSafetyDiagnostics.java`
- `src/main/java/com/crypto/funding/legacy/execution/*`

### 15.2 New domain
- `src/main/java/com/crypto/funding/domain/event/*`
- `src/main/java/com/crypto/funding/domain/trade/*`
- `src/main/java/com/crypto/funding/domain/execution/*`
- `src/main/java/com/crypto/funding/domain/profile/*`

### 15.3 New application layer
- `src/main/java/com/crypto/funding/application/event/*`
- `src/main/java/com/crypto/funding/application/trade/*`
- `src/main/java/com/crypto/funding/application/query/*`
- `src/main/java/com/crypto/funding/application/port/*`

### 15.4 New API
- `src/main/java/com/crypto/funding/api/FundingEventController.java`
- `src/main/java/com/crypto/funding/api/ArmedTradeController.java`
- `src/main/java/com/crypto/funding/api/ApiExceptionHandler.java`
- `src/main/java/com/crypto/funding/api/dto/*`

### 15.5 New persistence direction
- `src/main/java/com/crypto/funding/infrastructure/persistence/model/*`
- `src/main/java/com/crypto/funding/infrastructure/persistence/repository/*`
- `src/main/java/com/crypto/funding/infrastructure/persistence/mapper/*`
- `src/main/java/com/crypto/funding/infrastructure/persistence/converter/*`

### 15.6 Legacy stabilization
- `src/main/java/com/crypto/funding/scheduler/FundingSchedulerService.java`
- `src/main/java/com/crypto/funding/scheduler/OrderExecutorService.java`
- `src/main/java/com/crypto/funding/trading/TestOrderEngine.java`
- `src/main/java/com/crypto/funding/api/TestOrderController.java`
- `src/main/java/com/crypto/funding/api/TelegramBot.java`
- `src/main/java/com/crypto/funding/persistence/service/FundingApprovalService.java`
- `src/main/java/com/crypto/funding/watchlist/*`

### 15.7 Tests
- `src/test/java/com/crypto/funding/api/NewDomainApiIntegrationTest.java`
- `src/test/java/com/crypto/funding/domain/DomainModelInvariantTest.java`
- `src/test/java/com/crypto/funding/legacy/execution/LegacyExecutionGuardTest.java`
- `src/test/java/com/crypto/funding/persistence/NewDomainPersistenceTest.java`
- `src/test/java/com/crypto/funding/scheduler/OrderExecutorServiceSafetyTest.java`
- `src/test/java/com/crypto/funding/telegram/parser/FundingSignalParserTest.java`

---

## 16. Branch Diff Scope

Относительно `main` эта ветка включает крупную foundation-итерацию.

Сводно:
- 84 файла изменены;
- 3344 вставки;
- 74 удаления.

Это не косметический patch.
Но это и не полный greenfield rewrite.

По характеру изменений ветка соответствует цели:
- сохранить работающий ingest и buildability;
- добавить новый доменный фундамент;
- закрыть legacy risk;
- подготовить безопасную базу для следующего этапа.

---

## 17. What This Branch Means Strategically

Главный стратегический результат этой ветки в следующем:

### 17.1 The repository is now safer

Это важно не абстрактно, а операционно.
Можно локально запускать приложение без риска, что legacy flow неожиданно поставит реальный ордер из-за misleading defaults.

### 17.2 The repository is now more honest

Код, docs и runtime semantics теперь лучше отражают реальность:
- что legacy flow ещё существует;
- что он не является будущим ядром;
- что новый домен уже начат правильно;
- что Telegram пока живёт как ingest edge.

### 17.3 The repository now has a true migration path

До этой ветки следующий шаг был бы хаотичным: любой MVP пришлось бы строить поверх неправильной модели.

После этой ветки можно двигаться последовательно:
1. провести candidate-to-event bridging;
2. наращивать новый domain-driven execution path;
3. строить Gate-focused MVP уже в правильной архитектуре.

---

## 18. Recommended Next Steps After Phase 0-1

Следующая фаза не должна возвращаться к старой mental model.

Логичные Phase 2 направления:

### 18.1 Build a proper candidate-to-event flow
- определить, как Telegram candidate превращается в `FundingEvent`;
- решить, где происходит фильтрация и нормализация;
- отделить candidate observation от armed trade creation.

### 18.2 Build real adapters behind new ports
- `ExecutionPort`
- `OrderStatusPort`
- `MarketDataPort`
- `SymbolMetadataPort`

Это уже должно делаться под новый домен, а не через legacy scheduler semantics.

### 18.3 Introduce trade journaling and measurement
- измерение попыток входа и выхода;
- slippage;
- fill timing;
- outcome capture;
- profiling вокруг funding window.

### 18.4 Start Gate-focused MVP through the new domain
- не "оживлять" старый Gate path;
- а собрать первый реальный execution path вокруг `FundingEvent -> ArmedTrade -> OrderAttempt -> Position -> TradeOutcome`.

### 18.5 Define schema evolution strategy
- как дальше мигрировать SQLite schema;
- нужен ли Flyway/Liquibase;
- когда и как переходить к production-grade DB strategy.

---

## 19. What NotebookLM Should Focus On

Если загружать этот документ в NotebookLM, полезно отдельно исследовать такие вопросы:

1. Чем новый доменный словарь лучше старого `ApprovedFundingEntity`-центричного подхода?
2. Почему сохранение Telegram ingest совместимо с отказом от Telegram как control plane?
3. Как safe-by-default execution model меняет operational risk?
4. Что именно в этой ветке является foundation, а что ещё не является MVP?
5. Какие следующие шаги уже можно делать без возврата к legacy mental model?
6. Где проходит граница между transitional legacy code и новым ядром?

---

## 20. Suggested Reading Order

Чтобы понять ветку целиком, лучше читать в таком порядке:

1. `README.md`
2. `docs/00-overview.md`
3. `docs/02-runtime-config.md`
4. `docs/03-db-schema.md`
5. `docs/phase0-phase1-foundation.md`
6. `docs/phase0-phase1-implementation-brief.md`

После этого уже имеет смысл смотреть код в следующем порядке:
- `config/*`
- `legacy/execution/*`
- `domain/*`
- `application/*`
- `api/FundingEventController.java`
- `api/ArmedTradeController.java`
- `infrastructure/persistence/*`
- затем только legacy scheduler/execution pieces

---

## Final Summary

Phase 0-1 сделала три главные вещи:

1. Остановила опасное наследование неправильной execution-модели.
2. Ввела новый домен funding-event trading в код как реальную основу.
3. Сохранила Telegram ingest и общую buildability, не превращая фазу в разрушительный rewrite.

Это не фаза "делаем MVP".
Это фаза "перестаём строить на неправильном фундаменте".

Именно поэтому её главная ценность не в новой функциональности как таковой, а в том, что теперь следующую фазу можно строить на правильных границах, с меньшим риском и с честной моделью системы.
