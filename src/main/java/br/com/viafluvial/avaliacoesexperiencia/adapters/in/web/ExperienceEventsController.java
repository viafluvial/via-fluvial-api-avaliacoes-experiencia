package br.com.viafluvial.avaliacoesexperiencia.adapters.in.web;

import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.api.ExperienceEventsApi;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.CommandResponse;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.FreightDeliveredEvent;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.TripCompletedEvent;
import br.com.viafluvial.avaliacoesexperiencia.application.model.FreightDeliveredCommand;
import br.com.viafluvial.avaliacoesexperiencia.application.service.FreightEvaluationService;
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
    private final FreightEvaluationService freightService;
    public ExperienceEventsController(TripEventService service, FreightEvaluationService freightService) {
        this.service = service; this.freightService = freightService;
    }
    /** freight.delivered from api-fretes-cargas: SERVICE only (also enforced by URL in SecurityConfig). */
    @Override @PreAuthorize("hasRole('SERVICE')") public ResponseEntity<CommandResponse> receiveFreightDelivered(FreightDeliveredEvent event, String key) {
        boolean created = freightService.receiveDelivered(new FreightDeliveredCommand(event.getEventId(), event.getShipmentId(),
                event.getBoatmanId(), event.getContractorUserId(), event.getRecipientUserId(), event.getDeliveredAt()));
        return ResponseEntity.accepted().body(new CommandResponse(created ? "EVENT_ACCEPTED" : "EVENT_ALREADY_PROCESSED",
                MDC.get("correlationId"), OffsetDateTime.now()).resourceId(event.getShipmentId()));
    }
    @Override public ResponseEntity<CommandResponse> receiveTripCompleted(TripCompletedEvent event, String key) {
        boolean created = service.receiveCompleted(event.getEventId(), event.getTripId(), event.getCompletedAt());
        return ResponseEntity.accepted().body(new CommandResponse(created ? "EVENT_ACCEPTED" : "EVENT_ALREADY_PROCESSED",
                MDC.get("correlationId"), OffsetDateTime.now()));
    }
}