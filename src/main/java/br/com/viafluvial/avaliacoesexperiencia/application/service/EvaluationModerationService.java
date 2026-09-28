package br.com.viafluvial.avaliacoesexperiencia.application.service;

import br.com.viafluvial.avaliacoesexperiencia.application.model.PageResult;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.EvaluationRepositoryPort;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.OutboxPort;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.EvaluationNotFoundException;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationModerationService {

    public record Indicators(double average, long count, Double nps, Map<Integer, Long> distribution,
            long submitted, long published, long rejected, long underReview) {}

    private final EvaluationRepositoryPort repository;
    private final OutboxPort outbox;
    private final Clock clock;

    public EvaluationModerationService(EvaluationRepositoryPort repository, OutboxPort outbox, Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResult<ExperienceEvaluation> list(EvaluationStatus status, Integer rating, int page, int size) {
        return repository.findForModeration(status, rating, page, size);
    }

    @Transactional(readOnly = true)
    public ExperienceEvaluation get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new EvaluationNotFoundException("Evaluation not found"));
    }

    @Transactional
    public ExperienceEvaluation approve(UUID id, String reason) {
        var evaluation = get(id);
        evaluation.publish(reason, now());
        var saved = repository.save(evaluation);
        outbox.append("EVALUATION_PUBLISHED", id, Map.of("evaluationId", id));
        return saved;
    }

    @Transactional
    public ExperienceEvaluation reject(UUID id, String reason) {
        var evaluation = get(id);
        evaluation.reject(reason, now());
        var saved = repository.save(evaluation);
        outbox.append("EVALUATION_REJECTED", id, Map.of("evaluationId", id, "reason", reason));
        return saved;
    }

    @Transactional
    public void resolveContest(UUID contestId, boolean accepted, String reason) {
        outbox.append("EVALUATION_CONTEST_RESOLVED", contestId,
                Map.of("contestId", contestId, "accepted", accepted, "reason", reason));
    }

    @Transactional(readOnly = true)
    public Indicators indicators() {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int rating = 1; rating <= 5; rating++) distribution.put(rating, repository.countPublishedByRating(rating));
        Double avgNps = repository.averagePublishedNps();
        Double nps = avgNps == null ? null : Math.max(-100, Math.min(100, (avgNps - 5) * 20));
        return new Indicators(repository.averagePublished(), repository.countPublished(), nps, distribution,
                repository.countByStatus(EvaluationStatus.SUBMITTED), repository.countByStatus(EvaluationStatus.PUBLISHED),
                repository.countByStatus(EvaluationStatus.REJECTED), repository.countByStatus(EvaluationStatus.UNDER_REVIEW));
    }

    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
}