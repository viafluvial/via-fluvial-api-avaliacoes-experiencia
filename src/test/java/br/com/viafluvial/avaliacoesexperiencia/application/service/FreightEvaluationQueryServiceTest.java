package br.com.viafluvial.avaliacoesexperiencia.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.viafluvial.avaliacoesexperiencia.application.model.PageResult;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.EvaluationRepositoryPort;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.FreightEligibilityRepositoryPort;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.OutboxPort;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Internal read of freight evaluations (GET /internal/avaliacoes-frete). */
class FreightEvaluationQueryServiceTest {

    private EvaluationRepositoryPort repository;
    private FreightEvaluationService service;
    private final PageResult<ExperienceEvaluation> empty = new PageResult<>(List.of(), 0, 20, 0, 0);

    @BeforeEach
    void setUp() {
        repository = mock(EvaluationRepositoryPort.class);
        service = new FreightEvaluationService(repository, mock(FreightEligibilityRepositoryPort.class),
                mock(OutboxPort.class), Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC), 30);
        when(repository.findFreight(any(), any(), anyInt(), anyInt())).thenReturn(empty);
    }

    @Test
    void delegatesFiltersAndPaginationToTheFreightQuery() {
        UUID boatmanId = UUID.randomUUID();

        assertThat(service.list(boatmanId, EvaluationStatus.PUBLISHED, 2, 50)).isSameAs(empty);

        verify(repository).findFreight(boatmanId, EvaluationStatus.PUBLISHED, 2, 50);
    }

    @Test
    void appliesDefaultPageAndSizeWhenOmitted() {
        service.list(null, null, null, null);

        verify(repository).findFreight(null, null, 0, FreightEvaluationService.DEFAULT_PAGE_SIZE);
    }

    @Test
    void acceptsTheMaximumPageSize() {
        service.list(null, null, 0, FreightEvaluationService.MAX_PAGE_SIZE);

        verify(repository).findFreight(null, null, 0, 100);
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> service.list(null, null, -1, 20)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
        verify(repository, never()).findFreight(any(), any(), anyInt(), anyInt());
    }

    @Test
    void rejectsPageSizeOutsideOneToOneHundred() {
        assertThatThrownBy(() -> service.list(null, null, 0, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
        assertThatThrownBy(() -> service.list(null, null, 0, 101)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
        verify(repository, never()).findFreight(any(), any(), anyInt(), anyInt());
    }
}
