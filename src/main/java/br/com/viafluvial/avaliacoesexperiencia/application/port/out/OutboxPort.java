package br.com.viafluvial.avaliacoesexperiencia.application.port.out;

import java.util.Map;
import java.util.UUID;

public interface OutboxPort {
    void append(String eventType, UUID aggregateId, Map<String, Object> payload);
    boolean registerIncoming(UUID eventId, String eventType, String payload);
}