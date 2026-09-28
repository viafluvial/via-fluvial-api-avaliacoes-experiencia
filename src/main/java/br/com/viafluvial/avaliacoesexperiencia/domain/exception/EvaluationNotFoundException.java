package br.com.viafluvial.avaliacoesexperiencia.domain.exception;

public class EvaluationNotFoundException extends RuntimeException {

    public EvaluationNotFoundException(String message) {
        super(message);
    }
}