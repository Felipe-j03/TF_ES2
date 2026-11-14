package br.pucrs.es2.notificacoes.mensageria;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/** Contrato JSON publicado pelo microsserviço de acompanhamento. */
@JsonIgnoreProperties(ignoreUnknown = true)
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
}
