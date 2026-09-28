package br.com.viafluvial.avaliacoesexperiencia.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.viafluvial.avaliacoesexperiencia.application.model.CreateEvaluationCommand;
import br.com.viafluvial.avaliacoesexperiencia.application.model.EligibilityDecision;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.EligibilityGatewayPort;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.EvaluationRepositoryPort;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.OutboxPort;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.IneligibleEvaluationException;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EligibilityEvidence;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScore;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ManifestationType;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PassengerEvaluationServiceTest {

    private EvaluationRepositoryPort repository;
    private EligibilityGatewayPort gateway;
    private OutboxPort outbox;
    private PassengerEvaluationService service;
    private final UUID passengerId = UUID.randomUUID();
    private final UUID tripId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(EvaluationRepositoryPort.class);
        gateway = mock(EligibilityGatewayPort.class);
        outbox = mock(OutboxPort.class);
        var clock = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);
        service = new PassengerEvaluationService(repository, gateway, outbox, clock);
    }

    @Test
    void createsVerifiedEvaluationAndOutboxEvent() {
        var evidence = new EligibilityEvidence(tripId, passengerId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), OffsetDateTime.now().minusDays(1),
                OffsetDateTime.parse("2026-09-08T12:00:00Z"));
        when(repository.findActiveByPassengerAndTrip(passengerId, tripId)).thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey(passengerId, "request-123")).thenReturn(Optional.empty());
        when(gateway.verify(passengerId, tripId)).thenReturn(EligibilityDecision.allowed(evidence));
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(command("request-123"));

        assertThat(result.tripId()).isEqualTo(tripId);
        assertThat(result.passengerId()).isEqualTo(passengerId);
        verify(outbox).append(org.mockito.ArgumentMatchers.eq("EVALUATION_SUBMITTED"),
                org.mockito.ArgumentMatchers.eq(result.id()), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void failsClosedWhenEligibilityIsDenied() {
        when(repository.findActiveByPassengerAndTrip(passengerId, tripId)).thenReturn(Optional.empty());
        when(gateway.verify(passengerId, tripId)).thenReturn(EligibilityDecision.denied(tripId, "NO_EMBARKED_TICKET", false));

        assertThatThrownBy(() -> service.create(command(null)))
                .isInstanceOf(IneligibleEvaluationException.class)
                .hasMessage("NO_EMBARKED_TICKET");
    }

    private CreateEvaluationCommand command(String key) {
        var scores = new EvaluationScores(new EvaluationScore(5), null, null, null, null, null, null);
        return new CreateEvaluationCommand(tripId, passengerId, scores, null, ManifestationType.COMPLIMENT,
                "Excelente", List.of("ATENDIMENTO"), true, key);
    }
}