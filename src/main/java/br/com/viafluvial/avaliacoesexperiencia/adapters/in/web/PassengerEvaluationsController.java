package br.com.viafluvial.avaliacoesexperiencia.adapters.in.web;

import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.api.PassengerEvaluationsApi;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.CommandResponse;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.CreateEvaluationRequest;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EligibilityResponse;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EvaluationPage;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EvaluationResponse;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EvaluationStatus;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.ReasonRequest;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.UpdateEvaluationRequest;
import br.com.viafluvial.avaliacoesexperiencia.application.model.CreateEvaluationCommand;
import br.com.viafluvial.avaliacoesexperiencia.application.service.PassengerEvaluationService;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ManifestationType;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.NpsScore;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('PASSAGEIRO')")
public class PassengerEvaluationsController implements PassengerEvaluationsApi {
    private final PassengerEvaluationService service;
    private final AuthenticatedActor actor;
    private final EvaluationWebMapper mapper;

    public PassengerEvaluationsController(PassengerEvaluationService service, AuthenticatedActor actor,
            EvaluationWebMapper mapper) {
        this.service = service; this.actor = actor; this.mapper = mapper;
    }

    @Override public ResponseEntity<EligibilityResponse> getTripEvaluationEligibility(UUID tripId) {
        var decision = service.checkEligibility(actor.id(), tripId);
        var response = new EligibilityResponse(tripId, decision.eligible(), decision.reasonCode(), decision.alreadyEvaluated());
        if (decision.evidence() != null) response.eligibleUntil(decision.evidence().eligibleUntil())
                .bookingId(decision.evidence().bookingId()).ticketId(decision.evidence().ticketId());
        return ResponseEntity.ok(response);
    }

    @Override public ResponseEntity<EvaluationResponse> createEvaluation(CreateEvaluationRequest request, String key) {
        var command = new CreateEvaluationCommand(request.getTripId(), actor.id(), mapper.toDomain(request.getScores()),
                request.getNpsScore() == null ? null : new NpsScore(request.getNpsScore()),
                ManifestationType.valueOf(request.getManifestationType().name()), request.getComment(),
                request.getTags() == null ? java.util.List.of() : new ArrayList<>(request.getTags()),
                request.getAnonymousPublicDisplay(), key);
        var created = service.create(command);
        return ResponseEntity.created(URI.create("/api/v1/avaliacoes-experiencia/avaliacoes/" + created.id()))
                .body(mapper.toResponse(created));
    }

    @Override public ResponseEntity<EvaluationPage> listMyEvaluations(Integer page, Integer size, EvaluationStatus status) {
        var domainStatus = status == null ? null : br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus.valueOf(status.name());
        return ResponseEntity.ok(mapper.toPage(service.listMine(actor.id(), domainStatus, page, size)));
    }

    @Override public ResponseEntity<EvaluationResponse> getEvaluation(UUID evaluationId) {
        return ResponseEntity.ok(mapper.toResponse(service.getOwned(actor.id(), evaluationId)));
    }

    @Override public ResponseEntity<EvaluationResponse> updateEvaluation(UUID id, UpdateEvaluationRequest request) {
        var updated = service.update(actor.id(), id, mapper.toDomain(request.getScores()),
                request.getNpsScore() == null ? null : new NpsScore(request.getNpsScore()),
                request.getManifestationType() == null ? null : ManifestationType.valueOf(request.getManifestationType().name()),
                request.getComment(), request.getTags() == null ? null : new ArrayList<>(request.getTags()),
                request.getAnonymousPublicDisplay());
        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    @Override public ResponseEntity<EvaluationResponse> anonymizeEvaluation(UUID id) {
        return ResponseEntity.ok(mapper.toResponse(service.anonymize(actor.id(), id)));
    }

    @Override public ResponseEntity<CommandResponse> reportEvaluation(UUID id, ReasonRequest request) {
        UUID reportId = service.report(actor.id(), id, request.getReason());
        return ResponseEntity.created(URI.create("/api/v1/avaliacoes-experiencia/denuncias/" + reportId))
                .body(command("REPORT_CREATED", reportId.toString()));
    }

    private CommandResponse command(String code, String message) {
        return new CommandResponse(code, MDC.get("correlationId"), OffsetDateTime.now()).message(message);
    }
}