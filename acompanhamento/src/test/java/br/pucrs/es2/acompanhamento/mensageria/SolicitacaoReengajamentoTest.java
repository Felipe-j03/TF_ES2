package br.pucrs.es2.acompanhamento.mensageria;

import br.pucrs.es2.acompanhamento.moodle.dto.Aluno;
import br.pucrs.es2.acompanhamento.moodle.dto.Tarefa;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SolicitacaoReengajamentoTest {

    private final Tarefa tarefa = new Tarefa(10, 55, 2, "ES2", "T1",
            Instant.parse("2026-10-01T23:59:00Z"), false, "http://localhost:8080/mod/assign/view.php?id=55");
    private final Aluno aluno = new Aluno(7, "Aluno Teste 1", "aluno1@example.com");
    private final Instant agora = Instant.parse("2026-10-01T20:00:00Z");

    @Test
    void chaveDeIdempotenciaECursoTarefaAluno() {
        var s = SolicitacaoReengajamento.de(tarefa, aluno, agora);
        assertThat(s.idempotencyKey()).isEqualTo("2:10:7");
        assertThat(s.linkTarefa()).isEqualTo(tarefa.link());
        assertThat(s.alunoEmail()).isEqualTo("aluno1@example.com");
        assertThat(s.geradoEm()).isEqualTo(agora);
    }

    @Test
    void serializaDatasEmIso8601() {
        String json = JsonMapper.builder().build()
                .writeValueAsString(SolicitacaoReengajamento.de(tarefa, aluno, agora));
        assertThat(json).contains("\"prazo\":\"2026-10-01T23:59:00Z\"");
        assertThat(json).contains("\"idempotencyKey\":\"2:10:7\"");
    }
}
