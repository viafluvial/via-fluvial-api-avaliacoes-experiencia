package br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence;

import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.entity.OutboxEventEntity;
import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.entity.ProcessedEventEntity;
import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.repository.OutboxEventJpaRepository;
import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.repository.ProcessedEventJpaRepository;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.OutboxPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class OutboxPersistenceAdapter implements OutboxPort {

    private final OutboxEventJpaRepository outboxRepository;
    private final ProcessedEventJpaRepository processedRepository;
    private final ObjectMapper objectMapper;

    public OutboxPersistenceAdapter(OutboxEventJpaRepository outboxRepository,
            ProcessedEventJpaRepository processedRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.processedRepository = processedRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(String eventType, UUID aggregateId, Map<String, Object> payload) {
        var entity = new OutboxEventEntity();
        entity.setId(UUID.randomUUID()); entity.setEventType(eventType); entity.setAggregateId(aggregateId);
        entity.setPayload(json(payload)); entity.setStatus("PENDING"); entity.setAttempts(0);
        entity.setCreatedAt(OffsetDateTime.now()); entity.setNextAttemptAt(OffsetDateTime.now());
        outboxRepository.save(entity);
    }

    @Override
    public boolean registerIncoming(UUID eventId, String eventType, String payload) {
        if (processedRepository.existsById(eventId)) return false;
        var entity = new ProcessedEventEntity();
        entity.setEventId(eventId); entity.setEventType(eventType); entity.setPayloadHash(hash(payload));
        entity.setProcessedAt(OffsetDateTime.now());
        try {
            processedRepository.saveAndFlush(entity);
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }

    private String json(Map<String, Object> payload) {
        try { return objectMapper.writeValueAsString(payload); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Invalid outbox payload", exception); }
    }

    private String hash(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}