package br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.repository;

import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.entity.EvaluationEntity;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvaluationJpaRepository extends JpaRepository<EvaluationEntity, UUID>, JpaSpecificationExecutor<EvaluationEntity> {
    Optional<EvaluationEntity> findFirstByPassengerIdAndTripId(UUID passengerId, UUID tripId);
    Optional<EvaluationEntity> findFirstByPassengerIdAndIdempotencyKey(UUID passengerId, String idempotencyKey);
    long countByStatus(EvaluationStatus status);

    @Query("select coalesce(avg(e.overallScore), 0) from EvaluationEntity e where e.boatmanId = :boatmanId and e.status = 'PUBLISHED'")
    double averagePublishedByBoatman(@Param("boatmanId") UUID boatmanId);
    @Query("select count(e) from EvaluationEntity e where e.boatmanId = :boatmanId and e.status = 'PUBLISHED'")
    long countPublishedByBoatman(@Param("boatmanId") UUID boatmanId);
    @Query("select count(e) from EvaluationEntity e where e.boatmanId = :boatmanId and e.status = 'PUBLISHED' and e.overallScore = :rating")
    long countPublishedByBoatmanAndRating(@Param("boatmanId") UUID boatmanId, @Param("rating") int rating);
    @Query("select avg(e.npsScore) from EvaluationEntity e where e.boatmanId = :boatmanId and e.status = 'PUBLISHED' and e.npsScore is not null")
    Double averagePublishedNpsByBoatman(@Param("boatmanId") UUID boatmanId);
    @Query("select coalesce(avg(e.overallScore), 0) from EvaluationEntity e where e.status = 'PUBLISHED'")
    double averagePublished();
    @Query("select count(e) from EvaluationEntity e where e.status = 'PUBLISHED'")
    long countPublished();
    @Query("select count(e) from EvaluationEntity e where e.status = 'PUBLISHED' and e.overallScore = :rating")
    long countPublishedByRating(@Param("rating") int rating);
    @Query("select avg(e.npsScore) from EvaluationEntity e where e.status = 'PUBLISHED' and e.npsScore is not null")
    Double averagePublishedNps();
}