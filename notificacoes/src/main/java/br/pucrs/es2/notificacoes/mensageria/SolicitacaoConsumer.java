package br.pucrs.es2.notificacoes.mensageria;

import br.pucrs.es2.notificacoes.dominio.NotificacaoService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SolicitacaoConsumer {

    private final NotificacaoService notificacoes;

    public SolicitacaoConsumer(NotificacaoService notificacoes) {
        this.notificacoes = notificacoes;
    }

    @RabbitListener(queues = RabbitConfig.FILA)
    public void receber(SolicitacaoReengajamento solicitacao) {
        notificacoes.processar(solicitacao);
    }
}
