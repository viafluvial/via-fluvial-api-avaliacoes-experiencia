package br.com.viafluvial.avaliacoesexperiencia.adapters.in.web;

import br.com.viafluvial.avaliacoesexperiencia.domain.exception.EvaluationConflictException;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.EvaluationNotFoundException;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.IneligibleEvaluationException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(EvaluationNotFoundException.class)
    ProblemDetail notFound(EvaluationNotFoundException exception) { return problem(HttpStatus.NOT_FOUND, "Evaluation not found", exception, null); }
    @ExceptionHandler(EvaluationConflictException.class)
    ProblemDetail conflict(EvaluationConflictException exception) { return problem(HttpStatus.CONFLICT, "Evaluation conflict", exception, exception.code()); }
    /** The message of an ineligibility is already its stable reason code (e.g. NO_EMBARKED_TICKET, NOT_PARTICIPANT). */
    @ExceptionHandler(IneligibleEvaluationException.class)
    ProblemDetail ineligible(IneligibleEvaluationException exception) { return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Passenger is not eligible", exception, exception.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException exception) { return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception, null); }
    /** Bean validation of query/path parameters (e.g. size above 100) declared in the OpenAPI contract. */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException exception) { return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception, null); }
    private ProblemDetail problem(HttpStatus status, String title, Exception exception, String code) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title); detail.setType(URI.create("https://viafluvial.com.br/problems/" + status.value()));
        if (code != null && !code.isBlank()) detail.setProperty("code", code);
        return detail;
    }
}
