package br.pucrs.es2.notificacoes.dominio;

import br.pucrs.es2.notificacoes.config.NotificacoesProperties;
import br.pucrs.es2.notificacoes.email.SmtpNotificador;
import br.pucrs.es2.notificacoes.mensageria.SolicitacaoReengajamento;
import br.pucrs.es2.notificacoes.persistencia.NotificacaoPendente;
import br.pucrs.es2.notificacoes.persistencia.NotificacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class NotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);
    private static final int LIMITE_POR_CICLO = 100;

    private final NotificacaoRepository repository;
    private final SmtpNotificador notificador;
    private final JsonMapper jsonMapper;
    private final NotificacoesProperties props;
    private final Clock clock;

    public NotificacaoService(NotificacaoRepository repository, SmtpNotificador notificador,
                              JsonMapper jsonMapper, NotificacoesProperties props, Clock clock) {
        this.repository = repository;
        this.notificador = notificador;
        this.jsonMapper = jsonMapper;
        this.props = props;
        this.clock = clock;
    }

    public void processar(SolicitacaoReengajamento solicitacao) {
        validar(solicitacao);
        String payload;
        try {
            payload = jsonMapper.writeValueAsString(solicitacao);
        } catch (Exception erro) {
            throw new IllegalArgumentException("Não foi possível serializar a solicitação "
                    + solicitacao.idempotencyKey(), erro);
        }
        Instant agora = clock.instant();
        var inicio = repository.iniciar(solicitacao.idempotencyKey(), payload, agora, props.maxTentativas());
        if (!inicio.iniciado()) {
            log.info("Solicitação {} ignorada: já enviada, em processamento ou aguardando nova tentativa",
                    solicitacao.idempotencyKey());
            return;
        }

        try {
            notificador.enviar(solicitacao);
        } catch (RuntimeException erro) {
            registrarFalha(solicitacao.idempotencyKey(), inicio.tentativa(), erro);
            return;
        }

        repository.marcarEnviado(solicitacao.idempotencyKey(), clock.instant());
        log.info("Lembrete enviado para a solicitação {} (tentativa {})",
                solicitacao.idempotencyKey(), inicio.tentativa());
    }

    public void reprocessarFalhasVencidas() {
        Instant agora = clock.instant();
        List<NotificacaoPendente> pendentes = repository.buscarFalhasVencidas(
                agora, props.maxTentativas(), LIMITE_POR_CICLO);

        for (NotificacaoPendente pendente : pendentes) {
            try {
                SolicitacaoReengajamento solicitacao = jsonMapper.readValue(
                        pendente.payloadJson(), SolicitacaoReengajamento.class);
                processar(solicitacao);
            } catch (Exception erro) {
                repository.marcarFalhaIrrecuperavel(pendente.idempotencyKey(),
                        "Não foi possível recuperar a solicitação salva: " + resumoErro(erro), clock.instant());
                log.error("Solicitação {} marcada como falha irrecuperável: {}",
                        pendente.idempotencyKey(), erro.getClass().getSimpleName());
            }
        }
    }

    private void registrarFalha(String chave, int tentativa, RuntimeException erro) {
        boolean podeTentarNovamente = tentativa < props.maxTentativas();
        Instant proxima = podeTentarNovamente ? clock.instant().plus(props.intervaloRetentativa()) : null;
        repository.marcarFalha(chave, resumoErro(erro), proxima, clock.instant());
        if (podeTentarNovamente) {
            log.warn("Falha no envio da solicitação {} (tentativa {}/{}); próxima tentativa em {}. Tipo: {}",
                    chave, tentativa, props.maxTentativas(), proxima, erro.getClass().getSimpleName());
        } else {
            log.error("Solicitação {} falhou após {} tentativa(s). Tipo: {}",
                    chave, tentativa, erro.getClass().getSimpleName());
        }
    }

    private void validar(SolicitacaoReengajamento solicitacao) {
        if (solicitacao == null || solicitacao.idempotencyKey() == null
                || solicitacao.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("A solicitação precisa ter idempotencyKey");
        }
    }

    private String resumoErro(Exception erro) {
        String mensagem = erro.getMessage();
        return erro.getClass().getSimpleName() + (mensagem == null || mensagem.isBlank() ? "" : ": " + mensagem);
    }
}
