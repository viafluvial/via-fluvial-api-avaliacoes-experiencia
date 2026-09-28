package br.com.viafluvial.avaliacoesexperiencia.adapters.in.web;

import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.api.BoatmanExperienceApi;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.CommandResponse;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EvaluationPage;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.MessageRequest;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.RatingSummary;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.ReasonRequest;
import br.com.viafluvial.avaliacoesexperiencia.application.service.BoatmanExperienceService;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BoatmanExperienceController implements BoatmanExperienceApi {
    private final BoatmanExperienceService service;
    private final AuthenticatedActor actor;
    private final EvaluationWebMapper mapper;

    public BoatmanExperienceController(BoatmanExperienceService service, AuthenticatedActor actor, EvaluationWebMapper mapper) {
        this.service = service; this.actor = actor; this.mapper = mapper;
    }

    @Override public ResponseEntity<RatingSummary> getBoatmanSummary(UUID boatmanId) { return ResponseEntity.ok(summary(boatmanId)); }
    @Override @PreAuthorize("hasRole('GESTOR')") public ResponseEntity<RatingSummary> getMyBoatmanSummary() { return ResponseEntity.ok(summary(actor.id())); }
    @Override @PreAuthorize("hasRole('GESTOR')") public ResponseEntity<EvaluationPage> listMyBoatmanEvaluations(Integer page, Integer size) { return ResponseEntity.ok(mapper.toPage(service.list(actor.id(), page, size))); }
    @Override @PreAuthorize("hasRole('GESTOR')") public ResponseEntity<CommandResponse> respondToEvaluation(UUID id, MessageRequest request) {
        service.respond(actor.id(), id, request.getMessage());
        return ResponseEntity.created(URI.create("/api/v1/avaliacoes-experiencia/avaliacoes/" + id + "/resposta"))
                .body(command("RESPONSE_CREATED", id.toString()));
    }
    @Override @PreAuthorize("hasRole('GESTOR')") public ResponseEntity<CommandResponse> contestEvaluation(UUID id, ReasonRequest request) {
        UUID contestId = service.contest(actor.id(), id, request.getReason());
        return ResponseEntity.created(URI.create("/api/v1/avaliacoes-experiencia/contestacoes/" + contestId))
                .body(command("CONTEST_CREATED", contestId.toString()));
    }
    private RatingSummary summary(UUID id) {
        var value = service.summary(id);
        var distribution = new LinkedHashMap<String, Long>();
        value.distribution().forEach((key, count) -> distribution.put(key.toString(), count));
        return new RatingSummary(value.average(), value.count(), distribution, value.sampleSufficient()).nps(value.nps());
    }
    private CommandResponse command(String code, String message) { return new CommandResponse(code, MDC.get("correlationId"), OffsetDateTime.now()).message(message); }
}