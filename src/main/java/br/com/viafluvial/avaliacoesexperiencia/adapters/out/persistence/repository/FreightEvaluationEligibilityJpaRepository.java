package br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.repository;

import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.entity.FreightEvaluationEligibilityEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FreightEvaluationEligibilityJpaRepository extends JpaRepository<FreightEvaluationEligibilityEntity, UUID> {
}
