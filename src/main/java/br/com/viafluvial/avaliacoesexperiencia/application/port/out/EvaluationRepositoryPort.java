package br.com.viafluvial.avaliacoesexperiencia.application.port.out;

import br.com.viafluvial.avaliacoesexperiencia.application.model.BoatmanRating;
import br.com.viafluvial.avaliacoesexperiencia.application.model.FreightCriteriaAverages;
import br.com.viafluvial.avaliacoesexperiencia.application.model.PageResult;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.TargetType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Evaluation persistence. Methods without a {@link TargetType} argument keep their historical meaning and
 * consider TRIP evaluations only, so freight evaluations never change trip aggregates, lists or testimonials.
 */
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
    List<BoatmanRating> summarizePublishedByBoatmen(Collection<UUID> boatmanIds);
    List<ExperienceEvaluation> findPublishedTestimonials(int minScore, int minCommentLength, int limit);

    /** Inserts a new freight evaluation; a duplicate (rater, shipment) is reported as a conflict. */
    ExperienceEvaluation insertFreight(ExperienceEvaluation evaluation);
    Optional<ExperienceEvaluation> findFreightByPassengerAndShipment(UUID passengerId, UUID shipmentId);
    PageResult<ExperienceEvaluation> findPublishedByBoatman(UUID boatmanId, TargetType targetType, int page, int size);
    /** Moderation queue; a null targetType lists every kind of evaluation. */
    PageResult<ExperienceEvaluation> findForModeration(EvaluationStatus status, Integer rating, TargetType targetType,
            int page, int size);
    double averagePublishedByBoatman(UUID boatmanId, TargetType targetType);
    long countPublishedByBoatman(UUID boatmanId, TargetType targetType);
    long countPublishedByBoatmanAndRating(UUID boatmanId, TargetType targetType, int rating);
    FreightCriteriaAverages averagePublishedFreightCriteriaByBoatman(UUID boatmanId);
    /**
     * FREIGHT evaluations only, in any moderation status, newest first (createdAt desc, id desc). Null filters are
     * ignored.
     */
    PageResult<ExperienceEvaluation> findFreight(UUID boatmanId, EvaluationStatus status, int page, int size);
}