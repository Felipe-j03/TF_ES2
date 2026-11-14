package br.pucrs.es2.notificacoes.mensageria;

import br.pucrs.es2.notificacoes.dominio.NotificacaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetentativaScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentativaScheduler.class);

    private final NotificacaoService notificacoes;

    public RetentativaScheduler(NotificacaoService notificacoes) {
        this.notificacoes = notificacoes;
    }

    @Scheduled(fixedDelayString = "${notificacoes.intervalo-retentativa:PT30S}", initialDelayString = "PT10S")
    public void processarFalhasVencidas() {
        try {
            notificacoes.reprocessarFalhasVencidas();
        } catch (RuntimeException e) {
            log.error("Falha ao consultar solicitações de notificação para nova tentativa: {}", e.getMessage(), e);
        }
    }
}
