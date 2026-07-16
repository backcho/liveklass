.PHONY: up down migrate seed re-seed run test

up:
	@if ! command -v docker >/dev/null 2>&1; then \
		echo "Docker가 설치되어 있지 않습니다."; \
		exit 1; \
	fi
	docker compose up -d --wait

down:
	docker compose down

migrate:
	@if [ -z "$$(docker compose ps --status running --services)" ]; then \
		echo "Docker 컨테이너가 실행 중이 아닙니다. 먼저 'make up'을 실행하세요."; \
		exit 1; \
	fi
	./gradlew bootRun --args='--spring.profiles.active=local --spring.jpa.hibernate.ddl-auto=update --liveklass.migrate-only=true'

seed: migrate
	./gradlew bootRun --args='--spring.profiles.active=local --liveklass.seed.enabled=true'

re-seed:
	docker compose down -v && make up && make seed

run:
	@if [ -z "$$(docker compose ps --status running --services)" ]; then \
		echo "Docker 컨테이너가 실행 중이 아닙니다. 먼저 'make up'을 실행하세요."; \
		exit 1; \
	fi
	./gradlew bootRun --args='--spring.profiles.active=local'

test:
	@if ! command -v docker >/dev/null 2>&1; then \
		echo "Docker가 설치되어 있지 않습니다."; \
		exit 1; \
	fi
	./gradlew test
