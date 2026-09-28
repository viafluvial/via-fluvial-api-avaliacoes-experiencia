package br.com.viafluvial.avaliacoesexperiencia.domain.exception;

public class EvaluationConflictException extends RuntimeException {

    public EvaluationConflictException(String message) {
        super(message);
    }
}