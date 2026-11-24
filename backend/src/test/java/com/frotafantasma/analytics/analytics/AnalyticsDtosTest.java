package com.frotafantasma.analytics.analytics;

import static com.frotafantasma.analytics.analytics.AnalyticsDtos.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsDtosTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private Metric<Integer> metric(Origin origin, List<String> evidence) {
        return new Metric<>(2400, origin, Quality.AVAILABLE,
                new Period(null, null, "TEST"), 4, false, false, evidence, "fixture");
    }

    @Test void identificaFonteDiretaSemExigirMarcadorNaEvidencia() {
        assertThat(metric(Origin.DIRECT_API, List.of()).sources()).containsExactly(Origin.DIRECT_API);
        assertThat(metric(Origin.ROYALEAPI_IMPORT, List.of()).sources()).containsExactly(Origin.ROYALEAPI_IMPORT);
    }

    @Test void combinaFontesSemDuplicarEComOrdemEstavel() {
        var metric = metric(Origin.DERIVED, List.of(
                "war:1;source=ROYALEAPI_IMPORT;batch=123;resolution=4",
                "membership:2;source=DIRECT_API:donations=100",
                "war:3;source=ROYALEAPI_IMPORT;batch=123;resolution=4"));
        assertThat(metric.sources()).containsExactly(Origin.DIRECT_API, Origin.ROYALEAPI_IMPORT);
    }

    @Test void calculoDerivadoNaoInventaFonteQuandoNaoHaEvidencias() {
        for (var origin : List.of(Origin.DERIVED, Origin.SNAPSHOT_DERIVED, Origin.UNAVAILABLE)) {
            assertThat(metric(origin, List.of()).sources()).isEmpty();
        }
    }

    @Test void jsonPreservaEvidenciasERecalculaFontesEmVezDeAceitarValorEnviado() throws Exception {
        var original = metric(Origin.DERIVED, List.of("war:1;source=DIRECT_API"));
        ObjectNode document = json.valueToTree(original);
        assertThat(document.get("sources").toString()).isEqualTo("[\"DIRECT_API\"]");
        document.putArray("sources").add("ROYALEAPI_IMPORT");
        var restored = json.readValue(json.writeValueAsString(document), Metric.class);
        assertThat(restored.sources()).containsExactly(Origin.DIRECT_API);
        assertThat(restored.dataUsed()).isEqualTo(original.dataUsed());
        assertThat(restored.value()).isEqualTo(original.value());
        assertThat(restored.period()).isEqualTo(original.period());
    }

    @Test void preservaEvidenciasMesmoSeListaOriginalForAlterada() {
        var evidence = new ArrayList<>(List.of("war:1;source=DIRECT_API"));
        var metric = metric(Origin.DERIVED, evidence);
        evidence.clear();
        evidence.add("war:2;source=ROYALEAPI_IMPORT");
        assertThat(metric.dataUsed()).containsExactly("war:1;source=DIRECT_API");
        assertThat(metric.sources()).containsExactly(Origin.DIRECT_API);
        assertThatThrownBy(() -> metric.dataUsed().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
