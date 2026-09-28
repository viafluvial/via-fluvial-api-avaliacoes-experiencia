package br.com.viafluvial.avaliacoesexperiencia.adapters.in.web;

import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.api.EvaluationModerationApi;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.CommandResponse;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.ContestResolutionRequest;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EvaluationPage;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EvaluationResponse;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EvaluationStatus;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.ExperienceIndicators;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.ReasonRequest;
import br.com.viafluvial.avaliacoesexperiencia.application.service.EvaluationModerationService;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EvaluationModerationController implements EvaluationModerationApi {
    private final EvaluationModerationService service;
    private final EvaluationWebMapper mapper;
    public EvaluationModerationController(EvaluationModerationService service, EvaluationWebMapper mapper) { this.service = service; this.mapper = mapper; }
    @Override @PreAuthorize("hasRole('ADMINISTRADOR')") public ResponseEntity<EvaluationResponse> approveEvaluation(UUID id, ReasonRequest request) { return ResponseEntity.ok(mapper.toResponse(service.approve(id, request == null ? null : request.getReason()))); }
    @Override @PreAuthorize("hasRole('ADMINISTRADOR')") public ResponseEntity<EvaluationResponse> rejectEvaluation(UUID id, ReasonRequest request) { return ResponseEntity.ok(mapper.toResponse(service.reject(id, request.getReason()))); }
    @Override @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPORTE','AUDITOR')") public ResponseEntity<EvaluationResponse> getEvaluationForModeration(UUID id) { return ResponseEntity.ok(mapper.toResponse(service.get(id))); }
    @Override @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPORTE','AUDITOR')") public ResponseEntity<EvaluationPage> listEvaluationsForModeration(Integer page, Integer size, EvaluationStatus status, Integer rating) {
        var domainStatus = status == null ? null : br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus.valueOf(status.name());
        return ResponseEntity.ok(mapper.toPage(service.list(domainStatus, rating, page, size)));
    }
    @Override @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPORTE','AUDITOR')") public ResponseEntity<ExperienceIndicators> getExperienceIndicators() {
        var value = service.indicators(); var distribution = new LinkedHashMap<String, Long>();
        value.distribution().forEach((key, count) -> distribution.put(key.toString(), count));
        return ResponseEntity.ok(new ExperienceIndicators(value.average(), value.count(), distribution, value.count() >= 3,
                value.submitted(), value.published(), value.rejected(), value.underReview()).nps(value.nps()));
    }
    @Override @PreAuthorize("hasRole('ADMINISTRADOR')") public ResponseEntity<CommandResponse> resolveContest(UUID id, ContestResolutionRequest request) {
        service.resolveContest(id, request.getAccepted(), request.getReason());
        return ResponseEntity.ok(new CommandResponse("CONTEST_RESOLVED", MDC.get("correlationId"), OffsetDateTime.now()).message(id.toString()));
    }
}