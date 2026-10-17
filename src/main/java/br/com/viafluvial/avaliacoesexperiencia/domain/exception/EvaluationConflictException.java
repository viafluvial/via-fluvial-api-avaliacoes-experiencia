package br.com.viafluvial.avaliacoesexperiencia.domain.exception;

public class EvaluationConflictException extends RuntimeException {

    private final String code;

    public EvaluationConflictException(String message) {
        this(message, null);
    }

    public EvaluationConflictException(String message, String code) {
        super(message);
        this.code = code;
    }

    /** Stable error code (UPPER_SNAKE) when the conflict has one; null for legacy conflicts. */
    public String code() {
        return code;
    }
}
