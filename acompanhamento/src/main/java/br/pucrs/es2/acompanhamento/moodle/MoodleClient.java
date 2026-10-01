package br.pucrs.es2.acompanhamento.moodle;

import br.pucrs.es2.acompanhamento.moodle.dto.Aluno;
import br.pucrs.es2.acompanhamento.moodle.dto.Entrega;
import br.pucrs.es2.acompanhamento.moodle.dto.Tarefa;

import java.util.List;

public interface MoodleClient {

    List<Tarefa> buscarTarefas(long cursoId);

    /** Apenas usuários com permissão de enviar tarefas (alunos). */
    List<Aluno> buscarAlunos(long cursoId);

    List<Entrega> buscarEntregas(List<Long> tarefaIds);
}
