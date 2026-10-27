package br.com.viafluvial.avaliacoesexperiencia.adapters.in.web;

import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.api.BoatmanExperienceApi;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.BoatmanSummaryItem;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.BoatmanSummaryList;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.Testimonial;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.TestimonialList;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.CommandResponse;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EvaluationPage;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.FreightCriteriaAverages;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.TargetType;
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

    @Override public ResponseEntity<RatingSummary> getBoatmanSummary(UUID boatmanId, TargetType targetType) { return ResponseEntity.ok(summary(boatmanId, targetType)); }
    @Override public ResponseEntity<BoatmanSummaryList> listBoatmanSummaries(List<UUID> ids) {
        var items = service.summaries(ids).stream()
                .map(rating -> new BoatmanSummaryItem(rating.boatmanId(), rating.average(), rating.count(), rating.sampleSufficient()))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(new BoatmanSummaryList(items));
    }
    @Override public ResponseEntity<TestimonialList> listPublicTestimonials(Integer limit) {
        var items = service.testimonials(limit).stream()
                .map(evaluation -> new Testimonial(evaluation.id(), evaluation.scores().overall().value(),
                        evaluation.comment().trim(), evaluation.updatedAt()).tags(evaluation.tags()))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(new TestimonialList(items));
    }
    @Override @PreAuthorize("hasRole('GESTOR')") public ResponseEntity<RatingSummary> getMyBoatmanSummary(TargetType targetType) { return ResponseEntity.ok(summary(actor.id(), targetType)); }
    @Override @PreAuthorize("hasRole('GESTOR')") public ResponseEntity<EvaluationPage> listMyBoatmanEvaluations(Integer page, Integer size, TargetType targetType) {
        return ResponseEntity.ok(mapper.toPage(service.list(actor.id(), mapper.toDomain(targetType), page, size)));
    }
    /**
     * GESTOR responds as the authenticated boatman (original behaviour). A SERVICE caller (api-fretes-cargas on
     * behalf of the manager) must identify the boatman in X-Boatman-Id; ownership is still checked by the domain.
     */
    @Override @PreAuthorize("hasAnyRole('GESTOR','SERVICE')") public ResponseEntity<CommandResponse> respondToEvaluation(UUID id, MessageRequest request, UUID boatmanHeader) {
        UUID boatmanId;
        if (actor.hasRole("GESTOR")) {
            boatmanId = actor.id();
        } else if (boatmanHeader != null) {
            boatmanId = boatmanHeader;
        } else {
            throw new IllegalArgumentException("X-Boatman-Id is required for service calls");
        }
        service.respond(boatmanId, id, request.getMessage());
        return ResponseEntity.created(URI.create("/api/v1/avaliacoes-experiencia/avaliacoes/" + id + "/resposta"))
                .body(command("RESPONSE_CREATED", id.toString()));
    }
    @Override @PreAuthorize("hasRole('GESTOR')") public ResponseEntity<CommandResponse> contestEvaluation(UUID id, ReasonRequest request) {
        UUID contestId = service.contest(actor.id(), id, request.getReason());
        return ResponseEntity.created(URI.create("/api/v1/avaliacoes-experiencia/contestacoes/" + contestId))
                .body(command("CONTEST_CREATED", contestId.toString()));
    }
    private RatingSummary summary(UUID id, TargetType targetType) {
        var value = service.summary(id, br.com.viafluvial.avaliacoesexperiencia.domain.model.TargetType.orDefault(mapper.toDomain(targetType)));
        var distribution = new LinkedHashMap<String, Long>();
        value.distribution().forEach((key, count) -> distribution.put(key.toString(), count));
        var summary = new RatingSummary(value.average(), value.count(), distribution, value.sampleSufficient()).nps(value.nps())
                .targetType(mapper.toDto(value.targetType()));
        if (value.criteria() != null) {
            summary.criteria(new FreightCriteriaAverages().punctuality(value.criteria().punctuality())
                    .cargoCare(value.criteria().cargoCare()).service(value.criteria().service()));
        }
        return summary;
    }
    private CommandResponse command(String code, String message) { return new CommandResponse(code, MDC.get("correlationId"), OffsetDateTime.now()).message(message); }
}