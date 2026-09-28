package br.com.viafluvial.avaliacoesexperiencia.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.viafluvial.avaliacoesexperiencia.application.port.out.OutboxPort;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TripEventServiceTest {
    private final OutboxPort outbox = mock(OutboxPort.class);
    private final TripEventService service = new TripEventService(outbox);

    @Test
    void requestsInvitationOnlyOnFirstDelivery() {
        UUID eventId = UUID.randomUUID(); UUID tripId = UUID.randomUUID();
        when(outbox.registerIncoming(eventId, "TRIP_COMPLETED", tripId.toString())).thenReturn(true);
        assertThat(service.receiveCompleted(eventId, tripId, OffsetDateTime.parse("2026-08-09T12:00:00Z"))).isTrue();
        verify(outbox).append(eq("FEEDBACK_INVITATION_REQUESTED"), eq(tripId), anyMap());
    }

    @Test
    void ignoresDuplicateDelivery() {
        UUID eventId = UUID.randomUUID(); UUID tripId = UUID.randomUUID();
        assertThat(service.receiveCompleted(eventId, tripId, OffsetDateTime.parse("2026-08-09T12:00:00Z"))).isFalse();
        verify(outbox, never()).append(eq("FEEDBACK_INVITATION_REQUESTED"), eq(tripId), anyMap());
    }
}