package br.pucrs.es2.acompanhamento.dominio;

import br.pucrs.es2.acompanhamento.mensageria.SolicitacaoReengajamento;
import br.pucrs.es2.acompanhamento.moodle.dto.Aluno;
import br.pucrs.es2.acompanhamento.moodle.dto.Entrega;
import br.pucrs.es2.acompanhamento.moodle.dto.Tarefa;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        Set<ChaveEntrega> entregasEnviadas = new HashSet<>();
        for (Entrega entrega : entregas) {
            if (entrega.enviada()) {
                entregasEnviadas.add(new ChaveEntrega(entrega.tarefaId(), entrega.alunoId()));
            }
        }

        Instant limite = agora.plus(JANELA);
        List<SolicitacaoReengajamento> solicitacoes = new ArrayList<>();
        Set<String> chavesPublicadas = new HashSet<>();

        for (Tarefa tarefa : tarefas) {
            if (tarefa.prazo() == null || tarefa.emGrupo()
                    || !tarefa.prazo().isAfter(agora) || tarefa.prazo().isAfter(limite)) {
                continue;
            }

            for (Aluno aluno : alunos) {
                if (entregasEnviadas.contains(new ChaveEntrega(tarefa.id(), aluno.id()))) {
                    continue;
                }

                SolicitacaoReengajamento solicitacao = SolicitacaoReengajamento.de(tarefa, aluno, agora);
                if (chavesPublicadas.add(solicitacao.idempotencyKey())) {
                    solicitacoes.add(solicitacao);
                }
            }
        }

        return List.copyOf(solicitacoes);
    }

    private record ChaveEntrega(long tarefaId, long alunoId) {
    }
}
