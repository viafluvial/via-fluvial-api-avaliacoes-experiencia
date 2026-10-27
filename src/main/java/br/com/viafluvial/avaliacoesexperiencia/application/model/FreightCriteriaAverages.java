package br.com.viafluvial.avaliacoesexperiencia.application.model;

/** Averages of the freight criteria over published freight evaluations (null when there is none). */
public record FreightCriteriaAverages(Double punctuality, Double cargoCare, Double service) {

    public static FreightCriteriaAverages empty() {
        return new FreightCriteriaAverages(null, null, null);
    }
}
