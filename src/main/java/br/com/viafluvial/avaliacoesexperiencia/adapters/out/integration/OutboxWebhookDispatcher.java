package br.com.viafluvial.avaliacoesexperiencia.adapters.out.integration;

import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.entity.OutboxEventEntity;
import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.repository.OutboxEventJpaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "integrations.outbox.enabled", havingValue = "true")
public class OutboxWebhookDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxWebhookDispatcher.class);

    private final OutboxEventJpaRepository repository;
    private final TransactionTemplate transactions;
    private final RestClient client;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxWebhookDispatcher(OutboxEventJpaRepository repository, TransactionTemplate transactions,
            RestClient.Builder builder, Clock clock,
            @Value("${integrations.outbox.delivery-url}") String deliveryUrl,
            @Value("${integrations.outbox.batch-size:25}") int batchSize,
            @Value("${integrations.outbox.max-attempts:6}") int maxAttempts) {
        this.repository = repository;
        this.transactions = transactions;
        this.client = builder.baseUrl(deliveryUrl).build();
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${integrations.outbox.interval-millis:5000}")
    public void dispatch() {
        for (OutboxEventEntity event : claim()) {
            deliver(event);
        }
    }

    private List<OutboxEventEntity> claim() {
        List<OutboxEventEntity> claimed = transactions.execute(status -> {
            List<OutboxEventEntity> events = repository.claimable(OffsetDateTime.now(clock), batchSize);
            events.forEach(event -> event.setStatus("PROCESSING"));
            return repository.saveAll(events);
        });
        return claimed == null ? List.of() : claimed;
    }

    private void deliver(OutboxEventEntity event) {
        try {
            client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", event.getId().toString())
                    .body(Map.of(
                            "eventId", event.getId(),
                            "eventType", event.getEventType(),
                            "aggregateId", event.getAggregateId(),
                            "occurredAt", event.getCreatedAt(),
                            "payload", event.getPayload()))
                    .retrieve()
                    .toBodilessEntity();
            transactions.executeWithoutResult(status -> {
                OutboxEventEntity current = repository.findById(event.getId()).orElseThrow();
                current.setStatus("PUBLISHED");
                current.setPublishedAt(OffsetDateTime.now(clock));
                current.setLastError(null);
                repository.save(current);
            });
        } catch (RuntimeException exception) {
            retry(event.getId(), exception);
        }
    }

    private void retry(java.util.UUID eventId, RuntimeException exception) {
        transactions.executeWithoutResult(status -> {
            OutboxEventEntity current = repository.findById(eventId).orElseThrow();
            int attempts = current.getAttempts() + 1;
            current.setAttempts(attempts);
            current.setStatus(attempts >= maxAttempts ? "DEAD" : "RETRY");
            long delaySeconds = Math.min(300, 1L << Math.min(attempts, 8));
            current.setNextAttemptAt(OffsetDateTime.now(clock).plus(Duration.ofSeconds(delaySeconds)));
            current.setLastError(sanitize(exception.getMessage()));
            repository.save(current);
            LOGGER.warn("Outbox delivery failed eventId={} eventType={} attempt={} status={}",
                    current.getId(), current.getEventType(), attempts, current.getStatus());
        });
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) return "Delivery failed";
        String normalized = message.replaceAll("[\\r\\n]+", " ");
        return normalized.substring(0, Math.min(500, normalized.length()));
    }
}
