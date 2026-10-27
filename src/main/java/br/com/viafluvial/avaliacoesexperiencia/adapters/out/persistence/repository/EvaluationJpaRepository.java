package br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.repository;

import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.entity.EvaluationEntity;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.TargetType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every aggregate is scoped by target type so TRIP and FREIGHT evaluations never mix. */
public interface EvaluationJpaRepository extends JpaRepository<EvaluationEntity, UUID>, JpaSpecificationExecutor<EvaluationEntity> {
    Optional<EvaluationEntity> findFirstByPassengerIdAndTripId(UUID passengerId, UUID tripId);
    Optional<EvaluationEntity> findFirstByPassengerIdAndIdempotencyKey(UUID passengerId, String idempotencyKey);
    Optional<EvaluationEntity> findFirstByPassengerIdAndShipmentIdAndTargetType(UUID passengerId, UUID shipmentId, TargetType targetType);
    long countByStatusAndTargetType(EvaluationStatus status, TargetType targetType);

    @Query("select e.boatmanId, coalesce(avg(e.overallScore), 0), count(e) from EvaluationEntity e "
            + "where e.status = 'PUBLISHED' and e.targetType = :targetType and e.boatmanId in :boatmanIds group by e.boatmanId")
    java.util.List<Object[]> summarizePublishedByBoatmen(@Param("boatmanIds") java.util.Collection<UUID> boatmanIds,
            @Param("targetType") TargetType targetType);
    @Query("select coalesce(avg(e.overallScore), 0) from EvaluationEntity e where e.boatmanId = :boatmanId and e.status = 'PUBLISHED' and e.targetType = :targetType")
    double averagePublishedByBoatman(@Param("boatmanId") UUID boatmanId, @Param("targetType") TargetType targetType);
    @Query("select count(e) from EvaluationEntity e where e.boatmanId = :boatmanId and e.status = 'PUBLISHED' and e.targetType = :targetType")
    long countPublishedByBoatman(@Param("boatmanId") UUID boatmanId, @Param("targetType") TargetType targetType);
    @Query("select count(e) from EvaluationEntity e where e.boatmanId = :boatmanId and e.status = 'PUBLISHED' and e.targetType = :targetType and e.overallScore = :rating")
    long countPublishedByBoatmanAndRating(@Param("boatmanId") UUID boatmanId, @Param("targetType") TargetType targetType,
            @Param("rating") int rating);
    @Query("select avg(e.npsScore) from EvaluationEntity e where e.boatmanId = :boatmanId and e.status = 'PUBLISHED' and e.targetType = :targetType and e.npsScore is not null")
    Double averagePublishedNpsByBoatman(@Param("boatmanId") UUID boatmanId, @Param("targetType") TargetType targetType);
    @Query("select avg(e.scorePunctuality), avg(e.scoreCargoCare), avg(e.scoreFreightService) from EvaluationEntity e "
            + "where e.boatmanId = :boatmanId and e.status = 'PUBLISHED' and e.targetType = 'FREIGHT'")
    java.util.List<Object[]> averagePublishedFreightCriteriaByBoatman(@Param("boatmanId") UUID boatmanId);
    @Query("select coalesce(avg(e.overallScore), 0) from EvaluationEntity e where e.status = 'PUBLISHED' and e.targetType = :targetType")
    double averagePublished(@Param("targetType") TargetType targetType);
    @Query("select count(e) from EvaluationEntity e where e.status = 'PUBLISHED' and e.targetType = :targetType")
    long countPublished(@Param("targetType") TargetType targetType);
    @Query("select count(e) from EvaluationEntity e where e.status = 'PUBLISHED' and e.targetType = :targetType and e.overallScore = :rating")
    long countPublishedByRating(@Param("targetType") TargetType targetType, @Param("rating") int rating);
    @Query("select avg(e.npsScore) from EvaluationEntity e where e.status = 'PUBLISHED' and e.targetType = :targetType and e.npsScore is not null")
    Double averagePublishedNps(@Param("targetType") TargetType targetType);
}
