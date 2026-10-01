package br.pucrs.es2.acompanhamento.mensageria;

import br.pucrs.es2.acompanhamento.moodle.dto.Aluno;
import br.pucrs.es2.acompanhamento.moodle.dto.Tarefa;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SolicitacaoPublisherTest {

    @Test
    void publicaNaExchangeComRoutingKey() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        var publisher = new SolicitacaoPublisher(template);
        var s = SolicitacaoReengajamento.de(
                new Tarefa(10, 55, 2, "ES2", "T1", Instant.now(), false, "link"),
                new Aluno(7, "A", "a@example.com"), Instant.now());

        publisher.publicar(s);

        verify(template).convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, s);
    }
}
