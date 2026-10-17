package br.com.viafluvial.avaliacoesexperiencia.application.model;

import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.RaterRole;
import java.util.List;
import java.util.UUID;

/** Freight evaluation submitted by api-fretes-cargas on behalf of the contractor or recipient. */
public record CreateFreightEvaluationCommand(UUID shipmentId, UUID boatmanId, UUID raterUserId, RaterRole raterRole,
        FreightScores scores, List<String> tags, String comment, String idempotencyKey) {
}
