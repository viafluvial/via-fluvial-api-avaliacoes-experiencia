# API Avaliacoes e Experiencia

Microsservico responsavel por feedback verificado pos-viagem, moderacao, reputacao e indicadores da plataforma Via Fluvial.

## Requisitos

- Java 21
- Docker com Compose v2

## Executar

```bash
./mvnw clean verify
docker compose up --build -d
```

A API usa `http://localhost:18021/api/v1`. O PostgreSQL dedicado fica em `localhost:15452` apenas para desenvolvimento local.

- Health: `http://localhost:18021/api/v1/actuator/health`
- Swagger: `http://localhost:18021/api/v1/swagger-ui/index.html`
- OpenAPI runtime: `http://localhost:18021/api/v1/v3/api-docs`
- OpenAPI versionado: `http://localhost:18021/api/v1/openapi/openapi.yaml`

No perfil `dsv`, os headers `X-Dev-User-Id` e `X-Dev-Roles` permitem selecionar a identidade de teste. Sem headers, o principal e passageiro. Os perfis `hml` e `prd` exigem JWT emitido por `JWT_ISSUER_URI`.

O schema e criado por Flyway. Seeds existem apenas em `db/seed/dsv`; HML e PRD executam somente as migrations estruturais.
Como os seeds DSV ocupam a faixa V101-V199, esse perfil habilita `out-of-order` para permitir novas migrations estruturais na faixa V001-V099. HML e PRD mantem a ordenacao estrita.

## Integracoes

- `INTEGRATIONS_SERVICE_TOKEN`: bearer token enviado aos provedores de viagens, reservas e bilhetes. Deve ser fornecido por secret manager fora de DSV.
- `OUTBOX_ENABLED`: habilita o dispatcher da outbox; o padrao e `false`.
- `OUTBOX_DELIVERY_URL`: destino HTTPS dos eventos quando o dispatcher estiver habilitado.
- `OUTBOX_BATCH_SIZE`, `OUTBOX_MAX_ATTEMPTS` e `OUTBOX_INTERVAL_MILLIS`: controlam lote, tentativas e intervalo de entrega.

O dispatcher envia `Idempotency-Key` com o identificador do evento e move falhas permanentes para o estado `DEAD`. Nao habilite a entrega sem um destino real configurado.