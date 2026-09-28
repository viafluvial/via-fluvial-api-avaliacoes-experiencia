package br.com.viafluvial.avaliacoesexperiencia.application.service;

import br.com.viafluvial.avaliacoesexperiencia.application.model.CreateEvaluationCommand;
import br.com.viafluvial.avaliacoesexperiencia.application.model.EligibilityDecision;
import br.com.viafluvial.avaliacoesexperiencia.application.model.PageResult;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.EligibilityGatewayPort;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.EvaluationRepositoryPort;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.OutboxPort;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.EvaluationConflictException;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.EvaluationNotFoundException;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.IneligibleEvaluationException;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ManifestationType;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.NpsScore;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PassengerEvaluationService {

    private final EvaluationRepositoryPort repository;
    private final EligibilityGatewayPort eligibilityGateway;
    private final OutboxPort outbox;
    private final Clock clock;

    public PassengerEvaluationService(EvaluationRepositoryPort repository, EligibilityGatewayPort eligibilityGateway,
            OutboxPort outbox, Clock clock) {
        this.repository = repository;
        this.eligibilityGateway = eligibilityGateway;
        this.outbox = outbox;
        this.clock = clock;
    }

    public EligibilityDecision checkEligibility(UUID passengerId, UUID tripId) {
        if (repository.findActiveByPassengerAndTrip(passengerId, tripId).isPresent()) {
            return EligibilityDecision.denied(tripId, "ALREADY_EVALUATED", true);
        }
        return eligibilityGateway.verify(passengerId, tripId);
    }

    @Transactional
    public ExperienceEvaluation create(CreateEvaluationCommand command) {
        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            var existing = repository.findByIdempotencyKey(command.passengerId(), command.idempotencyKey());
            if (existing.isPresent()) return existing.get();
        }
        if (repository.findActiveByPassengerAndTrip(command.passengerId(), command.tripId()).isPresent()) {
            throw new EvaluationConflictException("Passenger already evaluated this trip");
        }
        EligibilityDecision decision = eligibilityGateway.verify(command.passengerId(), command.tripId());
        if (!decision.eligible() || decision.evidence() == null) {
            throw new IneligibleEvaluationException(decision.reasonCode());
        }
        var evaluation = ExperienceEvaluation.submit(decision.evidence(), command.scores(), command.npsScore(),
            command.manifestationType(), command.comment(), command.tags(), command.anonymousPublicDisplay(),
            command.idempotencyKey(), now());
        var saved = repository.save(evaluation);
        outbox.append("EVALUATION_SUBMITTED", saved.id(), Map.of(
                "evaluationId", saved.id(), "tripId", saved.tripId(), "status", saved.status().name(),
                "idempotencyKey", command.idempotencyKey() == null ? "" : command.idempotencyKey()));
        return saved;
    }

    @Transactional(readOnly = true)
    public ExperienceEvaluation getOwned(UUID passengerId, UUID evaluationId) {
        var evaluation = get(evaluationId);
        evaluation.requireOwner(passengerId);
        return evaluation;
    }

    @Transactional(readOnly = true)
    public PageResult<ExperienceEvaluation> listMine(UUID passengerId, EvaluationStatus status, int page, int size) {
        return repository.findByPassenger(passengerId, status, page, size);
    }

    @Transactional
    public ExperienceEvaluation update(UUID passengerId, UUID evaluationId, EvaluationScores scores,
            NpsScore npsScore, ManifestationType type, String comment, java.util.List<String> tags,
            Boolean anonymousPublicDisplay) {
        var evaluation = get(evaluationId);
        evaluation.requireOwner(passengerId);
        evaluation.update(scores, npsScore, type, comment, tags, anonymousPublicDisplay, now());
        var saved = repository.save(evaluation);
        outbox.append("EVALUATION_UPDATED", saved.id(), Map.of("evaluationId", saved.id()));
        return saved;
    }

    @Transactional
    public ExperienceEvaluation anonymize(UUID passengerId, UUID evaluationId) {
        var evaluation = get(evaluationId);
        evaluation.anonymize(passengerId, now());
        var saved = repository.save(evaluation);
        outbox.append("EVALUATION_ANONYMIZED", saved.id(), Map.of("evaluationId", saved.id()));
        return saved;
    }

    @Transactional
    public UUID report(UUID actorId, UUID evaluationId, String reason) {
        var evaluation = get(evaluationId);
        evaluation.flagForReview(now());
        repository.save(evaluation);
        UUID reportId = UUID.randomUUID();
        outbox.append("EVALUATION_REPORTED", evaluationId, Map.of(
                "reportId", reportId, "evaluationId", evaluationId, "actorId", actorId, "reason", reason));
        return reportId;
    }

    private ExperienceEvaluation get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new EvaluationNotFoundException("Evaluation not found"));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}