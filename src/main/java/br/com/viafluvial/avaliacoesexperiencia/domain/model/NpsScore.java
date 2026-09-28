package br.com.viafluvial.avaliacoesexperiencia.domain.model;

public record NpsScore(int value) {

    public NpsScore {
        if (value < 0 || value > 10) {
            throw new IllegalArgumentException("NPS score must be between 0 and 10");
        }
    }
}