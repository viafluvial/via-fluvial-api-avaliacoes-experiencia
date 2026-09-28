package br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.repository;

import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.entity.ProcessedEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, UUID> {
}