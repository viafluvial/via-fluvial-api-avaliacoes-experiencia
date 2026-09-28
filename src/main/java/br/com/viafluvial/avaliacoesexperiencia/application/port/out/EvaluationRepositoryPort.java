package br.com.viafluvial.avaliacoesexperiencia.application.port.out;

import br.com.viafluvial.avaliacoesexperiencia.application.model.PageResult;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import java.util.Optional;
import java.util.UUID;

public interface EvaluationRepositoryPort {
    ExperienceEvaluation save(ExperienceEvaluation evaluation);
    Optional<ExperienceEvaluation> findById(UUID id);
    Optional<ExperienceEvaluation> findActiveByPassengerAndTrip(UUID passengerId, UUID tripId);
    Optional<ExperienceEvaluation> findByIdempotencyKey(UUID passengerId, String idempotencyKey);
    PageResult<ExperienceEvaluation> findByPassenger(UUID passengerId, EvaluationStatus status, int page, int size);
    PageResult<ExperienceEvaluation> findPublishedByBoatman(UUID boatmanId, int page, int size);
    PageResult<ExperienceEvaluation> findForModeration(EvaluationStatus status, Integer rating, int page, int size);
    long countByStatus(EvaluationStatus status);
    double averagePublishedByBoatman(UUID boatmanId);
    long countPublishedByBoatman(UUID boatmanId);
    long countPublishedByBoatmanAndRating(UUID boatmanId, int rating);
    Double averagePublishedNpsByBoatman(UUID boatmanId);
    double averagePublished();
    long countPublished();
    long countPublishedByRating(int rating);
    Double averagePublishedNps();
}