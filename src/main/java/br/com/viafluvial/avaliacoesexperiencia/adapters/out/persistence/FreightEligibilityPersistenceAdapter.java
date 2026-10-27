package br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence;

import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.entity.FreightEvaluationEligibilityEntity;
import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.repository.FreightEvaluationEligibilityJpaRepository;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.FreightEligibilityRepositoryPort;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightEligibility;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FreightEligibilityPersistenceAdapter implements FreightEligibilityRepositoryPort {

    private final FreightEvaluationEligibilityJpaRepository repository;

    public FreightEligibilityPersistenceAdapter(FreightEvaluationEligibilityJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<FreightEligibility> findByShipmentId(UUID shipmentId) {
        return repository.findById(shipmentId).map(FreightEligibilityPersistenceAdapter::toDomain);
    }

    @Override
    public FreightEligibility save(FreightEligibility value) {
        var entity = new FreightEvaluationEligibilityEntity();
        entity.setShipmentId(value.shipmentId());
        entity.setBoatmanId(value.boatmanId());
        entity.setContractorUserId(value.contractorUserId());
        entity.setRecipientUserId(value.recipientUserId());
        entity.setDeliveredAt(value.deliveredAt());
        entity.setExpiresAt(value.expiresAt());
        entity.setEventId(value.eventId());
        entity.setCreatedAt(OffsetDateTime.now());
        return toDomain(repository.saveAndFlush(entity));
    }

    private static FreightEligibility toDomain(FreightEvaluationEligibilityEntity value) {
        return new FreightEligibility(value.getShipmentId(), value.getBoatmanId(), value.getContractorUserId(),
                value.getRecipientUserId(), value.getDeliveredAt(), value.getExpiresAt(), value.getEventId());
    }
}
