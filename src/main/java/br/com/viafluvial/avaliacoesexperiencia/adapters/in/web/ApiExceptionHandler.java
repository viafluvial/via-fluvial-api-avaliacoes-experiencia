package br.com.viafluvial.avaliacoesexperiencia.adapters.in.web;

import br.com.viafluvial.avaliacoesexperiencia.domain.exception.EvaluationConflictException;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.EvaluationNotFoundException;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.IneligibleEvaluationException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(EvaluationNotFoundException.class)
    ProblemDetail notFound(EvaluationNotFoundException exception) { return problem(HttpStatus.NOT_FOUND, "Evaluation not found", exception); }
    @ExceptionHandler(EvaluationConflictException.class)
    ProblemDetail conflict(EvaluationConflictException exception) { return problem(HttpStatus.CONFLICT, "Evaluation conflict", exception); }
    @ExceptionHandler(IneligibleEvaluationException.class)
    ProblemDetail ineligible(IneligibleEvaluationException exception) { return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Passenger is not eligible", exception); }
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException exception) { return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception); }
    private ProblemDetail problem(HttpStatus status, String title, Exception exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title); detail.setType(URI.create("https://viafluvial.com.br/problems/" + status.value()));
        return detail;
    }
}