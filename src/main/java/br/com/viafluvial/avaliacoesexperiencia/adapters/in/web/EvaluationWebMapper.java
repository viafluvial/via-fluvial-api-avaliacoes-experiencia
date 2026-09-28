package br.com.viafluvial.avaliacoesexperiencia.adapters.in.web;

import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EvaluationPage;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EvaluationResponse;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.Scores;
import br.com.viafluvial.avaliacoesexperiencia.application.model.PageResult;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScore;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import java.util.ArrayList;
import org.springframework.stereotype.Component;

@Component
public class EvaluationWebMapper {
    public EvaluationScores toDomain(Scores value) {
        if (value == null) return null;
        return new EvaluationScores(score(value.getOverall()), score(value.getPlatform()), score(value.getBoatman()),
                score(value.getVessel()), score(value.getRoute()), score(value.getBoarding()), score(value.getService()));
    }

    public EvaluationResponse toResponse(ExperienceEvaluation value) {
        Scores scores = new Scores(value.scores().overall().value())
                .platform(raw(value.scores().platform())).boatman(raw(value.scores().boatman()))
                .vessel(raw(value.scores().vessel())).route(raw(value.scores().route()))
                .boarding(raw(value.scores().boarding())).service(raw(value.scores().service()));
        var response = new EvaluationResponse(value.id(), value.tripId(), scores,
                br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.ManifestationType.valueOf(value.manifestationType().name()),
                br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EvaluationStatus.valueOf(value.status().name()),
                true, value.anonymousPublicDisplay(), value.createdAt(), value.updatedAt());
        return response.boatmanId(value.boatmanId()).vesselId(value.vesselId()).routeId(value.routeId())
                .npsScore(value.npsScore() == null ? null : value.npsScore().value()).comment(value.comment())
                .tags(new ArrayList<>(value.tags())).boatmanResponse(value.boatmanResponse())
                .moderationReason(value.moderationReason());
    }

    public EvaluationPage toPage(PageResult<ExperienceEvaluation> page) {
        return new EvaluationPage(page.items().stream().map(this::toResponse).toList(), page.page(), page.size(),
                page.totalElements(), page.totalPages());
    }

    private EvaluationScore score(Integer value) { return value == null ? null : new EvaluationScore(value); }
    private Integer raw(EvaluationScore value) { return value == null ? null : value.value(); }
}