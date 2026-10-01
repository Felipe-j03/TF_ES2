package br.pucrs.es2.acompanhamento.mensageria;

import br.pucrs.es2.acompanhamento.moodle.dto.Aluno;
import br.pucrs.es2.acompanhamento.moodle.dto.Tarefa;

import java.time.Instant;

/**
 * Mensagem publicada na fila para o microsserviço de notificações.
 * A idempotencyKey identifica o par curso/tarefa/aluno e evita reenvios.
 */
public record SolicitacaoReengajamento(
        String idempotencyKey,
        long cursoId,
        String cursoNome,
        long tarefaId,
        String tarefaNome,
        Instant prazo,
        String linkTarefa,
        long alunoId,
        String alunoNome,
        String alunoEmail,
        Instant geradoEm) {

    public static SolicitacaoReengajamento de(Tarefa t, Aluno a, Instant agora) {
        return new SolicitacaoReengajamento(
                t.cursoId() + ":" + t.id() + ":" + a.id(),
                t.cursoId(), t.cursoNome(),
                t.id(), t.nome(), t.prazo(), t.link(),
                a.id(), a.nomeCompleto(), a.email(),
                agora);
    }
}
