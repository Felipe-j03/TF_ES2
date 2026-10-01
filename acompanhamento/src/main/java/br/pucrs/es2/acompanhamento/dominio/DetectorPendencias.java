package br.pucrs.es2.acompanhamento.dominio;

import br.pucrs.es2.acompanhamento.mensageria.SolicitacaoReengajamento;
import br.pucrs.es2.acompanhamento.moodle.dto.Aluno;
import br.pucrs.es2.acompanhamento.moodle.dto.Entrega;
import br.pucrs.es2.acompanhamento.moodle.dto.Tarefa;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Decide quais alunos devem receber lembrete.
 *
 * Regras (ver DetectorPendenciasTest):
 * - só tarefas individuais com prazo entre agora e agora + 24h;
 * - ignora tarefas sem prazo, já vencidas ou em grupo;
 * - ignora alunos que já enviaram a tarefa (status "submitted");
 * - uma solicitação por aluno e tarefa.
 */
@Component
public class DetectorPendencias {

    public static final Duration JANELA = Duration.ofHours(24);

    public List<SolicitacaoReengajamento> detectar(List<Tarefa> tarefas, List<Aluno> alunos,
                                                   List<Entrega> entregas, Instant agora) {
        // TODO: implementar as regras acima e reativar os testes em DetectorPendenciasTest
        return List.of();
    }
}
