package br.com.viafluvial.avaliacoesexperiencia.adapters.out.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.entity.OutboxEventEntity;
import br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence.repository.OutboxEventJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

class OutboxWebhookDispatcherTest {

    private static final OffsetDateTime NOW = OffsetDateTime.ofInstant(
            Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);

    private OutboxEventJpaRepository repository;
    private TransactionTemplate transactions;
    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventJpaRepository.class);
        transactions = mock(TransactionTemplate.class);
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void publishesClaimedEventWithIdempotencyKey() {
        OutboxEventEntity event = event(0);
        when(repository.claimable(NOW, 25)).thenReturn(List.of(event));
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        server.expect(requestTo("http://delivery.test/events"))
                .andExpect(header("Idempotency-Key", event.getId().toString()))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        dispatcher(6).dispatch();

        server.verify();
        assertThat(event.getStatus()).isEqualTo("PUBLISHED");
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void schedulesRetryAfterDeliveryFailure() {
        OutboxEventEntity event = event(0);
        when(repository.claimable(NOW, 25)).thenReturn(List.of(event));
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        server.expect(requestTo("http://delivery.test/events")).andRespond(withServerError());

        dispatcher(6).dispatch();

        assertThat(event.getStatus()).isEqualTo("RETRY");
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(event.getLastError()).isNotBlank();
    }

    @Test
    void marksEventDeadAtAttemptLimit() {
        OutboxEventEntity event = event(5);
        when(repository.claimable(NOW, 25)).thenReturn(List.of(event));
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        server.expect(requestTo("http://delivery.test/events")).andRespond(withServerError());

        dispatcher(6).dispatch();

        assertThat(event.getStatus()).isEqualTo("DEAD");
        assertThat(event.getAttempts()).isEqualTo(6);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(64));
    }

    private OutboxWebhookDispatcher dispatcher(int maxAttempts) {
        return new OutboxWebhookDispatcher(repository, transactions, builder,
                Clock.fixed(NOW.toInstant(), ZoneOffset.UTC), "http://delivery.test/events", 25, maxAttempts);
    }

    private OutboxEventEntity event(int attempts) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setId(UUID.fromString("ae000001-0000-4000-8000-000000000099"));
        event.setAggregateId(UUID.fromString("ae000001-0000-4000-8000-000000000001"));
        event.setEventType("EvaluationPublished");
        event.setPayload("{\"evaluationId\":\"ae000001-0000-4000-8000-000000000001\"}");
        event.setStatus("PENDING");
        event.setAttempts(attempts);
        event.setCreatedAt(NOW.minusMinutes(1));
        event.setNextAttemptAt(NOW.minusSeconds(1));
        return event;
    }
}