package br.com.viafluvial.avaliacoesexperiencia.domain.model;

/** Kind of experience being evaluated. TRIP is the historical (default) behaviour. */
public enum TargetType {
    TRIP,
    FREIGHT;

    public static TargetType orDefault(TargetType value) {
        return value == null ? TRIP : value;
    }
}
