package br.com.viafluvial.avaliacoesexperiencia.application.model;

import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ManifestationType;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.NpsScore;
import java.util.List;
import java.util.UUID;

public record CreateEvaluationCommand(UUID tripId, UUID passengerId, EvaluationScores scores, NpsScore npsScore,
        ManifestationType manifestationType, String comment, List<String> tags,
        boolean anonymousPublicDisplay, String idempotencyKey) {
}