package br.com.viafluvial.avaliacoesexperiencia.domain.model;

import java.util.Objects;

/** The three freight criteria (1 to 5). */
public record FreightScores(EvaluationScore punctuality, EvaluationScore cargoCare, EvaluationScore service) {

    public FreightScores {
        Objects.requireNonNull(punctuality, "punctuality is required");
        Objects.requireNonNull(cargoCare, "cargoCare is required");
        Objects.requireNonNull(service, "service is required");
    }

    public static FreightScores of(int punctuality, int cargoCare, int service) {
        return new FreightScores(new EvaluationScore(punctuality), new EvaluationScore(cargoCare),
                new EvaluationScore(service));
    }

    /**
     * Generic overall score of a freight evaluation: arithmetic mean of the three criteria rounded half up
     * (4,33 -> 4; 4,67 -> 5). Keeps the existing integer 1..5 overall score meaningful for moderation filters
     * and distributions; the exact decimal mean is derivable from the criteria.
     */
    public EvaluationScore overall() {
        int sum = punctuality.value() + cargoCare.value() + service.value();
        return new EvaluationScore((int) Math.round(sum / 3.0));
    }

    /** Manifestation implied by the overall score: 4-5 compliment, 3 suggestion, 1-2 complaint. */
    public ManifestationType manifestationType() {
        int overall = overall().value();
        if (overall >= 4) return ManifestationType.COMPLIMENT;
        if (overall == 3) return ManifestationType.SUGGESTION;
        return ManifestationType.COMPLAINT;
    }
}
