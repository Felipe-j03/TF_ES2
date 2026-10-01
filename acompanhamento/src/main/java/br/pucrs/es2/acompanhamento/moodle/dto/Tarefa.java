package br.pucrs.es2.acompanhamento.moodle.dto;

import java.time.Instant;

/** Tarefa do Moodle. O prazo é nulo quando a tarefa não tem data de entrega. */
public record Tarefa(long id, long cmid, long cursoId, String cursoNome, String nome,
                     Instant prazo, boolean emGrupo, String link) {
}
