package br.com.viafluvial.avaliacoesexperiencia.application.service;

import br.com.viafluvial.avaliacoesexperiencia.application.port.out.OutboxPort;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripEventService {

    private final OutboxPort outbox;

    public TripEventService(OutboxPort outbox) {
        this.outbox = outbox;
    }

    @Transactional
    public boolean receiveCompleted(UUID eventId, UUID tripId, OffsetDateTime completedAt) {
        boolean firstDelivery = outbox.registerIncoming(eventId, "TRIP_COMPLETED", tripId.toString());
        if (firstDelivery) {
            outbox.append("FEEDBACK_INVITATION_REQUESTED", tripId,
                    Map.of("eventId", eventId, "tripId", tripId, "completedAt", completedAt.toString()));
        }
        return firstDelivery;
    }
}