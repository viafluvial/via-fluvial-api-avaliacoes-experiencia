package br.com.viafluvial.avaliacoesexperiencia.domain.model;

public record EvaluationScores(
        EvaluationScore overall,
        EvaluationScore platform,
        EvaluationScore boatman,
        EvaluationScore vessel,
        EvaluationScore route,
        EvaluationScore boarding,
        EvaluationScore service) {
}