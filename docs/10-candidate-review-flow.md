# Candidate Review Flow (Phase 2)

## Purpose

Phase 2 добавляет первый настоящий control flow нового домена:

`Telegram signal -> SignalCandidate -> Review -> FundingEvent`

Этот поток не запускает trade execution и не создаёт `ArmedTrade` автоматически.
Его задача — отделить ingest наблюдения от осознанного operator decision.

## What Was Added

- новый candidate-домен:
  - `SignalCandidate`
  - `SignalCandidateStatus`
  - `ReviewDecision`
- новый application слой:
  - `SignalCandidateIngestService`
  - `SignalCandidateReviewService`
  - `SignalCandidateQueryService`
  - `SymbolNormalizationService`
- persistence для `signal_candidate`
- provenance link `SignalCandidate -> FundingEvent`
- review API:
  - `GET /api/v1/candidates`
  - `GET /api/v1/candidates/{id}`
  - `POST /api/v1/candidates/{id}/approve`
  - `POST /api/v1/candidates/{id}/reject`

## Ingest Behavior

- Telegram signal по-прежнему обновляет watchlist.
- Дополнительно каждый funding candidate message создаёт или повторно использует `SignalCandidate`.
- Dedupe:
  - сначала по `sourceType + sourceChatId + sourceMessageId`
  - fallback по `sourceType + rawSymbol + dedupe window`

## Normalization

- parser остаётся источником сырого символа;
- финальная нормализация вынесена в application слой;
- `SymbolNormalizationService` приводит raw symbol к canonical form;
- venue hints определяются через `SymbolMetadataPort`;
- текущая Phase 2 использует bootstrap metadata adapter как временный exchange-driven источник.

## Review Semantics

- approve/reject доступны только через REST;
- Telegram bot не делает approve/create event actions;
- `FundingEvent` создаётся только после approve;
- если `fundingTime` не передан в approve request, сервис пытается взять его из funding watchlist для выбранной venue;
- если venue неоднозначна, нужен explicit override.

## Out of Scope

- live execution
- auto-create `FundingEvent` из сигнала
- auto-create `ArmedTrade`
- Gate live adapter rewrite
- timing engine
- risk engine
