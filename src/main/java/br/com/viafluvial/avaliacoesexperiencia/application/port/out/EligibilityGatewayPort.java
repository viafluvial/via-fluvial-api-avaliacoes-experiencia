package br.com.viafluvial.avaliacoesexperiencia.application.port.out;

import br.com.viafluvial.avaliacoesexperiencia.application.model.EligibilityDecision;
import java.util.UUID;

public interface EligibilityGatewayPort {
    EligibilityDecision verify(UUID passengerId, UUID tripId);
}