# Telegram via TDLib

## Что делает
- читает сообщения из канала
- извлекает символы (например BTC/USDT)
- кладёт в watchlist

## Ограничения
- TDLib = нативная библиотека
- для CI/Docker используем classifier `linux_amd64_gnu_ssl3` (передаётся в build arg `TD_NATIVES`)
- локально osdetector выберет нативки по платформе (можно override `-PtdNativesClassifier=...`)

## Хранилище сессии
- /data/tdlib (volume)
- в контейнере должен сохраняться state (иначе каждую перезагрузку login)
