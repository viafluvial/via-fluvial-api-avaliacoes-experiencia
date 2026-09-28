package br.com.viafluvial.avaliacoesexperiencia.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EvaluationScoreTest {

    @Test
    void acceptsScoresWithinTheScale() {
        assertThat(new EvaluationScore(1).value()).isEqualTo(1);
        assertThat(new EvaluationScore(5).value()).isEqualTo(5);
    }

    @Test
    void rejectsScoresOutsideTheScale() {
        assertThatThrownBy(() -> new EvaluationScore(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvaluationScore(6))
                .isInstanceOf(IllegalArgumentException.class);
    }
}