package br.com.viafluvial.avaliacoesexperiencia.application.port.out;

import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightEligibility;
import java.util.Optional;
import java.util.UUID;

public interface FreightEligibilityRepositoryPort {
    Optional<FreightEligibility> findByShipmentId(UUID shipmentId);
    FreightEligibility save(FreightEligibility eligibility);
}
