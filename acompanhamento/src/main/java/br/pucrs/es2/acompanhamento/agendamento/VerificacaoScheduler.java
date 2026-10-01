package br.pucrs.es2.acompanhamento.agendamento;

import br.pucrs.es2.acompanhamento.dominio.DetectorPendencias;
import br.pucrs.es2.acompanhamento.mensageria.SolicitacaoPublisher;
import br.pucrs.es2.acompanhamento.mensageria.SolicitacaoReengajamento;
import br.pucrs.es2.acompanhamento.moodle.MoodleClient;
import br.pucrs.es2.acompanhamento.moodle.MoodleProperties;
import br.pucrs.es2.acompanhamento.moodle.dto.Aluno;
import br.pucrs.es2.acompanhamento.moodle.dto.Entrega;
import br.pucrs.es2.acompanhamento.moodle.dto.Tarefa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class VerificacaoScheduler {

    private static final Logger log = LoggerFactory.getLogger(VerificacaoScheduler.class);

    private final MoodleClient moodle;
    private final DetectorPendencias detector;
    private final SolicitacaoPublisher publisher;
    private final MoodleProperties props;
    private final Clock clock;

    public VerificacaoScheduler(MoodleClient moodle, DetectorPendencias detector, SolicitacaoPublisher publisher,
                                MoodleProperties props, Clock clock) {
        this.moodle = moodle;
        this.detector = detector;
        this.publisher = publisher;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${acompanhamento.intervalo}", initialDelayString = "PT10S")
    public void executar() {
        long cursoId = props.cursoId();
        try {
            List<Tarefa> tarefas = moodle.buscarTarefas(cursoId);
            List<Aluno> alunos = moodle.buscarAlunos(cursoId);
            List<Entrega> entregas = moodle.buscarEntregas(tarefas.stream().map(Tarefa::id).toList());
            Instant agora = clock.instant();

            List<SolicitacaoReengajamento> solicitacoes = detector.detectar(tarefas, alunos, entregas, agora);
            solicitacoes.forEach(publisher::publicar);

            log.info("Verificação do curso {}: {} tarefas, {} alunos, {} entregas, {} solicitações publicadas",
                    cursoId, tarefas.size(), alunos.size(), entregas.size(), solicitacoes.size());
        } catch (RuntimeException e) {
            log.error("Falha na verificação do curso {}: {}", cursoId, e.getMessage(), e);
        }
    }
}
