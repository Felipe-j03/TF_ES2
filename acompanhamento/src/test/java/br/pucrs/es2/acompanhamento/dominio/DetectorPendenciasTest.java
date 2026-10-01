package br.pucrs.es2.acompanhamento.dominio;

import br.pucrs.es2.acompanhamento.mensageria.SolicitacaoReengajamento;
import br.pucrs.es2.acompanhamento.moodle.dto.Aluno;
import br.pucrs.es2.acompanhamento.moodle.dto.Entrega;
import br.pucrs.es2.acompanhamento.moodle.dto.Tarefa;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Especificação da regra de reengajamento.
 * Remover o @Disabled de cada teste à medida que a regra for implementada.
 */
class DetectorPendenciasTest {

    private final DetectorPendencias detector = new DetectorPendencias();
    private final Instant agora = Instant.parse("2026-10-01T12:00:00Z");
    private final Aluno ana = new Aluno(7, "Ana", "ana@example.com");
    private final Aluno bruno = new Aluno(8, "Bruno", "bruno@example.com");

    private Tarefa tarefaVencendoEm(Duration d) {
        return new Tarefa(10, 55, 2, "ES2", "T1", agora.plus(d), false, "link");
    }

    @Test
    @Disabled("implementar DetectorPendencias")
    void geraSolicitacaoParaAlunoSemEntregaDentroDaJanela() {
        var r = detector.detectar(List.of(tarefaVencendoEm(Duration.ofHours(2))), List.of(ana), List.of(), agora);
        assertThat(r).extracting(SolicitacaoReengajamento::idempotencyKey).containsExactly("2:10:7");
    }

    @Test
    @Disabled("implementar DetectorPendencias")
    void ignoraAlunoQueJaEnviou() {
        var r = detector.detectar(List.of(tarefaVencendoEm(Duration.ofHours(2))), List.of(ana, bruno),
                List.of(new Entrega(10, 7, "submitted")), agora);
        assertThat(r).extracting(SolicitacaoReengajamento::alunoId).containsExactly(8L);
    }

    @Test
    @Disabled("implementar DetectorPendencias")
    void rascunhoNaoContaComoEnvio() {
        var r = detector.detectar(List.of(tarefaVencendoEm(Duration.ofHours(2))), List.of(ana),
                List.of(new Entrega(10, 7, "draft")), agora);
        assertThat(r).hasSize(1);
    }

    @Test
    @Disabled("implementar DetectorPendencias")
    void ignoraTarefaForaDaJanela() {
        var r = detector.detectar(List.of(tarefaVencendoEm(Duration.ofHours(25))), List.of(ana), List.of(), agora);
        assertThat(r).isEmpty();
    }

    @Test
    @Disabled("implementar DetectorPendencias")
    void ignoraTarefaJaVencida() {
        var r = detector.detectar(List.of(tarefaVencendoEm(Duration.ofHours(-1))), List.of(ana), List.of(), agora);
        assertThat(r).isEmpty();
    }

    @Test
    @Disabled("implementar DetectorPendencias")
    void ignoraTarefaSemPrazo() {
        var semPrazo = new Tarefa(10, 55, 2, "ES2", "T1", null, false, "link");
        assertThat(detector.detectar(List.of(semPrazo), List.of(ana), List.of(), agora)).isEmpty();
    }

    @Test
    @Disabled("implementar DetectorPendencias")
    void ignoraTarefaEmGrupo() {
        var emGrupo = new Tarefa(10, 55, 2, "ES2", "T1", agora.plus(Duration.ofHours(2)), true, "link");
        assertThat(detector.detectar(List.of(emGrupo), List.of(ana), List.of(), agora)).isEmpty();
    }
}
