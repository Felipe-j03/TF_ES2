package br.pucrs.es2.acompanhamento.agendamento;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "acompanhamento.agendamento.habilitado", havingValue = "true", matchIfMissing = true)
class AgendamentoConfig {
}
