package br.com.viafluvial.avaliacoesexperiencia.domain.model;

public record EvaluationScore(int value) {

    public EvaluationScore {
        if (value < 1 || value > 5) {
            throw new IllegalArgumentException("Evaluation score must be between 1 and 5");
        }
    }
}