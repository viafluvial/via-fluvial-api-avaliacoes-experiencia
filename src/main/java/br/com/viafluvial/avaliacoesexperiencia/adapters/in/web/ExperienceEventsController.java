package br.com.viafluvial.avaliacoesexperiencia.adapters.in.web;

import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.api.ExperienceEventsApi;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.CommandResponse;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.TripCompletedEvent;
import br.com.viafluvial.avaliacoesexperiencia.application.service.TripEventService;
import java.time.OffsetDateTime;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyRole('SERVICE','ADMINISTRADOR')")
public class ExperienceEventsController implements ExperienceEventsApi {
    private final TripEventService service;
    public ExperienceEventsController(TripEventService service) { this.service = service; }
    @Override public ResponseEntity<CommandResponse> receiveTripCompleted(TripCompletedEvent event, String key) {
        boolean created = service.receiveCompleted(event.getEventId(), event.getTripId(), event.getCompletedAt());
        return ResponseEntity.accepted().body(new CommandResponse(created ? "EVENT_ACCEPTED" : "EVENT_ALREADY_PROCESSED",
                MDC.get("correlationId"), OffsetDateTime.now()));
    }
}