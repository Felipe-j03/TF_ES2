package br.pucrs.es2.acompanhamento.agendamento;

import br.pucrs.es2.acompanhamento.dominio.DetectorPendencias;
import br.pucrs.es2.acompanhamento.mensageria.SolicitacaoPublisher;
import br.pucrs.es2.acompanhamento.mensageria.SolicitacaoReengajamento;
import br.pucrs.es2.acompanhamento.moodle.MoodleClient;
import br.pucrs.es2.acompanhamento.moodle.MoodleException;
import br.pucrs.es2.acompanhamento.moodle.MoodleProperties;
import br.pucrs.es2.acompanhamento.moodle.dto.Aluno;
import br.pucrs.es2.acompanhamento.moodle.dto.Entrega;
import br.pucrs.es2.acompanhamento.moodle.dto.Tarefa;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificacaoSchedulerTest {

    private final MoodleClient moodle = mock(MoodleClient.class);
    private final DetectorPendencias detector = mock(DetectorPendencias.class);
    private final SolicitacaoPublisher publisher = mock(SolicitacaoPublisher.class);
    private final Instant agora = Instant.parse("2026-10-01T20:00:00Z");
    private final VerificacaoScheduler scheduler = new VerificacaoScheduler(
            moodle, detector, publisher,
            new MoodleProperties("u", "u", "t", 2),
            Clock.fixed(agora, ZoneOffset.UTC));

    @Test
    void buscaDadosDoCursoDetectaEPublicaCadaSolicitacao() {
        var tarefa = new Tarefa(10, 55, 2, "ES2", "T1", agora.plusSeconds(3600), false, "link");
        var aluno = new Aluno(7, "A", "a@example.com");
        var entrega = new Entrega(10, 8, "submitted");
        var solicitacao = SolicitacaoReengajamento.de(tarefa, aluno, agora);

        when(moodle.buscarTarefas(2)).thenReturn(List.of(tarefa));
        when(moodle.buscarAlunos(2)).thenReturn(List.of(aluno));
        when(moodle.buscarEntregas(List.of(10L))).thenReturn(List.of(entrega));
        when(detector.detectar(List.of(tarefa), List.of(aluno), List.of(entrega), agora))
                .thenReturn(List.of(solicitacao));

        scheduler.executar();

        verify(publisher).publicar(solicitacao);
    }

    @Test
    void erroNoMoodleNaoPublicaENaoPropaga() {
        when(moodle.buscarTarefas(2)).thenThrow(new MoodleException("invalidtoken", "x"));

        scheduler.executar();

        verify(publisher, never()).publicar(any());
    }
}
