package br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.repository;

import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.entity.OutboxEventEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {
	@Query(value = """
			SELECT * FROM "sc-avaliacoes-experiencia".outbox_event
			WHERE status IN ('PENDING', 'RETRY') AND next_attempt_at <= :now
			ORDER BY created_at
			FOR UPDATE SKIP LOCKED
			LIMIT :batchSize
			""", nativeQuery = true)
	List<OutboxEventEntity> claimable(@Param("now") OffsetDateTime now, @Param("batchSize") int batchSize);
}