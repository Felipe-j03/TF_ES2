package br.pucrs.es2.acompanhamento.mensageria;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class SolicitacaoPublisher {

    private final RabbitTemplate rabbitTemplate;

    public SolicitacaoPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicar(SolicitacaoReengajamento solicitacao) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, solicitacao);
    }
}
