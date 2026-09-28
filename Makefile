MAVENW=./mvnw

.PHONY: build verify test run up down logs

build:
	$(MAVENW) clean package -DskipTests

verify:
	$(MAVENW) clean verify

test:
	$(MAVENW) test

run:
	$(MAVENW) spring-boot:run -Dspring-boot.run.profiles=dsv

up:
	docker compose up --build -d

down:
	docker compose down -v

logs:
	docker compose logs -f api-avaliacoes-experiencia