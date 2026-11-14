package br.pucrs.es2.notificacoes.mensageria;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "reengajamento";
    public static final String FILA = "reengajamento.solicitacoes";
    public static final String ROUTING_KEY = "solicitacao";

    @Bean
    DirectExchange reengajamentoExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue filaSolicitacoes() {
        return QueueBuilder.durable(FILA).build();
    }

    @Bean
    Binding bindingSolicitacoes(Queue filaSolicitacoes, DirectExchange reengajamentoExchange) {
        return BindingBuilder.bind(filaSolicitacoes).to(reengajamentoExchange).with(ROUTING_KEY);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
