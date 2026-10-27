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
## Avaliacao de frete (targetType FREIGHT)

A mesma tabela `experience_evaluation` registra avaliacoes de viagem (`target_type = TRIP`, padrao e comportamento original) e de frete entregue (`FREIGHT`), usadas pela `api-fretes-cargas` (V006/V007).

- `POST /avaliacoes-experiencia/internal/eventos/frete-entregue` (somente `ROLE_SERVICE`): recebe `{eventId, shipmentId, boatmanId, contractorUserId, recipientUserId, deliveredAt}` e grava `freight_evaluation_eligibility` com `expires_at = deliveredAt + 30 dias` (`FREIGHT_EVALUATION_WINDOW_DAYS`). Idempotente por `eventId` via `processed_event` (`202 EVENT_ACCEPTED` / `202 EVENT_ALREADY_PROCESSED`); a remessa mantem a janela do primeiro evento.
- `POST /avaliacoes-experiencia/internal/avaliacoes-frete` (somente `ROLE_SERVICE`, em nome do usuario): `{shipmentId, boatmanId, raterUserId, raterRole (CONTRATANTE|DESTINATARIO), punctuality, cargoCare, service, tags[], comment, idempotencyKey}` -> `201 EvaluationResponse` com `targetType=FREIGHT`, `shipmentId` e `freightScores`. Erros `ProblemDetail` com `code`: 422 `SHIPMENT_NOT_DELIVERED`, `EVALUATION_WINDOW_EXPIRED`, `BOATMAN_MISMATCH`, `NOT_PARTICIPANT`; 409 `ALREADY_EVALUATED` (uma por remessa e pessoa) e `IDEMPOTENCY_KEY_REUSED`. A mesma `idempotencyKey` (corpo ou `Idempotency-Key`) devolve a mesma avaliacao.
- `GET /avaliacoes-experiencia/internal/avaliacoes-frete?boatmanId=&status=&page=&size=` (somente `ROLE_SERVICE`): leitura paginada (`FreightEvaluationPage`: `items, page, size, totalElements, totalPages`) somente das avaliacoes `FREIGHT`, em qualquer estado de moderacao, da mais recente para a mais antiga. Cada item traz `id, targetType, shipmentId, boatmanId, raterUserId, rating, freightScores, comment, tags, status, moderationReason, boatmanResponse, createdAt, updatedAt`. `boatmanId` invalido, `status` desconhecido, `page < 0` ou `size` fora de 1..100 -> 400 (padrao `page=0`, `size=20`). Usado pela api-fretes-cargas para espelhar moderacao e resposta sem precisar do papel `GESTOR`.
- `passenger_id` guarda o `raterUserId`; `scores.overall` = media das tres notas arredondada (meio para cima); `manifestationType` derivado da nota geral (4-5 COMPLIMENT, 3 SUGGESTION, 1-2 COMPLAINT); exibicao publica sempre anonima. A avaliacao segue a moderacao existente (SUBMITTED/UNDER_REVIEW -> PUBLISHED/REJECTED).
- `GET /barqueiros/{boatmanId}/resumo`, `/barqueiros/me/resumo` e `/barqueiros/me/avaliacoes` aceitam `targetType=TRIP|FREIGHT` (padrao TRIP). O resumo FREIGHT traz `criteria` (medias de pontualidade, cuidado com a carga e atendimento). Agregados de viagem, `/barqueiros/resumos`, depoimentos, indicadores administrativos e `/avaliacoes/minhas` consideram somente TRIP. A fila `GET /admin/avaliacoes` lista os dois tipos (filtro opcional `targetType`).
- `POST /avaliacoes/{id}/resposta`: `GESTOR` responde como o usuario autenticado (sem mudanca); `SERVICE` pode responder em nome do gestor informando `X-Boatman-Id`.
- Outbox: avaliacao de frete emite `FREIGHT_EVALUATION_SUBMITTED` (nunca `EVALUATION_SUBMITTED`, que continua exclusivo de viagem com `tripId`); `EVALUATION_PUBLISHED`, `EVALUATION_REJECTED` e `BOATMAN_RESPONSE_CREATED` de frete incluem `targetType` e `shipmentId`. O evento frete-entregue nao gera convite (o convite `freight.rating.requested` e do fretes-cargas).
