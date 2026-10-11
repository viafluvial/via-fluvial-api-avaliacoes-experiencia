package br.com.viafluvial.avaliacoesexperiencia.application.service;

import br.com.viafluvial.avaliacoesexperiencia.application.model.BoatmanRating;
import br.com.viafluvial.avaliacoesexperiencia.application.model.PageResult;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.EvaluationRepositoryPort;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.OutboxPort;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.EvaluationNotFoundException;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BoatmanExperienceService {

    public record Summary(double average, long count, Double nps, Map<Integer, Long> distribution, boolean sampleSufficient) {}

    public static final int MAX_SUMMARY_IDS = 50;
    public static final int DEFAULT_TESTIMONIALS = 6;
    public static final int MAX_TESTIMONIALS = 12;
    static final int TESTIMONIAL_MIN_SCORE = 4;
    static final int TESTIMONIAL_MIN_COMMENT_LENGTH = 20;

    private final EvaluationRepositoryPort repository;
    private final OutboxPort outbox;
    private final Clock clock;

    public BoatmanExperienceService(EvaluationRepositoryPort repository, OutboxPort outbox, Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID boatmanId) {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int rating = 1; rating <= 5; rating++) {
            distribution.put(rating, repository.countPublishedByBoatmanAndRating(boatmanId, rating));
        }
        long count = repository.countPublishedByBoatman(boatmanId);
        return new Summary(repository.averagePublishedByBoatman(boatmanId), count,
                toNps(repository.averagePublishedNpsByBoatman(boatmanId)), distribution, count >= 3);
    }

    @Transactional(readOnly = true)
    public PageResult<ExperienceEvaluation> list(UUID boatmanId, int page, int size) {
        return repository.findPublishedByBoatman(boatmanId, page, size);
    }

    /** Public aggregate per requested boatman (zeroed when there is no published evaluation). */
    @Transactional(readOnly = true)
    public List<BoatmanRating> summaries(List<UUID> boatmanIds) {
        List<UUID> ids = boatmanIds == null ? List.of()
                : boatmanIds.stream().filter(Objects::nonNull).distinct().limit(MAX_SUMMARY_IDS).toList();
        if (ids.isEmpty()) return List.of();
        Map<UUID, BoatmanRating> found = new HashMap<>();
        repository.summarizePublishedByBoatmen(ids).forEach(rating -> found.put(rating.boatmanId(), rating));
        return ids.stream().map(id -> found.getOrDefault(id, new BoatmanRating(id, 0, 0))).toList();
    }

    /** Well-rated published comments for the public home page; the passenger is never identified. */
    @Transactional(readOnly = true)
    public List<ExperienceEvaluation> testimonials(Integer limit) {
        int size = limit == null ? DEFAULT_TESTIMONIALS : Math.max(1, Math.min(MAX_TESTIMONIALS, limit));
        return repository.findPublishedTestimonials(TESTIMONIAL_MIN_SCORE, TESTIMONIAL_MIN_COMMENT_LENGTH, size);
    }

    @Transactional
    public void respond(UUID boatmanId, UUID evaluationId, String response) {
        var evaluation = get(evaluationId);
        evaluation.respond(boatmanId, response, now());
        repository.save(evaluation);
        outbox.append("BOATMAN_RESPONSE_CREATED", evaluationId, Map.of("evaluationId", evaluationId, "boatmanId", boatmanId));
    }

    @Transactional
    public UUID contest(UUID boatmanId, UUID evaluationId, String reason) {
        var evaluation = get(evaluationId);
        evaluation.contest(boatmanId, now());
        repository.save(evaluation);
        UUID contestId = UUID.randomUUID();
        outbox.append("EVALUATION_CONTESTED", evaluationId, Map.of(
                "contestId", contestId, "evaluationId", evaluationId, "boatmanId", boatmanId, "reason", reason));
        return contestId;
    }

    private Double toNps(Double averageNps) {
        if (averageNps == null) return null;
        return Math.max(-100, Math.min(100, (averageNps - 5) * 20));
    }

    private ExperienceEvaluation get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new EvaluationNotFoundException("Evaluation not found"));
    }

    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
}