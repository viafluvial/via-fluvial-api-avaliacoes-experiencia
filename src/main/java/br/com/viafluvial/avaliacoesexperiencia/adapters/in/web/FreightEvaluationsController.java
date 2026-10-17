package br.com.viafluvial.avaliacoesexperiencia.adapters.in.web;

import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.api.FreightEvaluationsApi;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.CreateFreightEvaluationRequest;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EvaluationResponse;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EvaluationStatus;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.FreightEvaluationPage;
import br.com.viafluvial.avaliacoesexperiencia.application.model.CreateFreightEvaluationCommand;
import br.com.viafluvial.avaliacoesexperiencia.application.service.FreightEvaluationService;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.RaterRole;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoints used by api-fretes-cargas (platform service credential, ROLE_SERVICE only): registering a freight
 * evaluation on behalf of the contractor or recipient of a shipment and reading freight evaluations back.
 */
@RestController
@PreAuthorize("hasRole('SERVICE')")
public class FreightEvaluationsController implements FreightEvaluationsApi {
    private final FreightEvaluationService service;
    private final EvaluationWebMapper mapper;

    public FreightEvaluationsController(FreightEvaluationService service, EvaluationWebMapper mapper) {
        this.service = service; this.mapper = mapper;
    }

    /** Paginated FREIGHT evaluations (any moderation status) for api-fretes-cargas to mirror moderation and replies. */
    @Override
    public ResponseEntity<FreightEvaluationPage> listFreightEvaluations(UUID boatmanId, EvaluationStatus status,
            Integer page, Integer size) {
        var domainStatus = status == null ? null
                : br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus.valueOf(status.name());
        return ResponseEntity.ok(mapper.toFreightPage(service.list(boatmanId, domainStatus, page, size)));
    }

    @Override
    public ResponseEntity<EvaluationResponse> createFreightEvaluation(CreateFreightEvaluationRequest request, String key) {
        String idempotencyKey = request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()
                ? request.getIdempotencyKey() : key;
        List<String> tags = request.getTags() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(request.getTags()));
        var command = new CreateFreightEvaluationCommand(request.getShipmentId(), request.getBoatmanId(),
                request.getRaterUserId(), RaterRole.valueOf(request.getRaterRole().name()),
                FreightScores.of(request.getPunctuality(), request.getCargoCare(), request.getService()),
                tags, request.getComment(), idempotencyKey);
        var created = service.create(command);
        return ResponseEntity.created(URI.create("/api/v1/avaliacoes-experiencia/avaliacoes/" + created.id()))
                .body(mapper.toResponse(created));
    }
}
