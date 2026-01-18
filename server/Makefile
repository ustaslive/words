.PHONY: up down tail restart

up:
	docker compose up -d --build

down:
	docker compose down

tail:
	docker compose logs -f --tail=0

restart:
	docker compose up -d --build
