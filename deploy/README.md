cd deploy
cp .env.example .env  # заполнить TG_*, *_API_KEY и т.п.
docker compose up -d
docker logs -f funding-app

Состав:
- funding-app (jar из образа), volume ./data:/data
- grafana/prometheus отсутствуют — мониторинг по желанию
