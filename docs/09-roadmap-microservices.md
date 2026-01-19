# Roadmap to Microservices (later)

## Split candidates
1) Scheduler service
- читает approved_funding
- эмитит "execute funding" job

2) Execution service
- принимает job
- делает ордера по биржам
- пишет execution result

## Transport
- пока можно через DB polling
- позже Kafka/Redis queue

## Почему сейчас монолит
- меньше latency, проще дебаг, 1 контейнер, MVP быстрее
