package br.com.viafluvial.avaliacoesexperiencia.application.model;

import java.util.UUID;

/** Published rating aggregate of a boatman, used by public showcases. */
public record BoatmanRating(UUID boatmanId, double average, long count) {

    public static final long MIN_PUBLIC_SAMPLE = 3;

    public boolean sampleSufficient() {
        return count >= MIN_PUBLIC_SAMPLE;
    }
}
