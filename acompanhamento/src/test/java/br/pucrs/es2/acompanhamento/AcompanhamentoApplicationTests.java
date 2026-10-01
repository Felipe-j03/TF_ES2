package br.pucrs.es2.acompanhamento;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "acompanhamento.agendamento.habilitado=false")
class AcompanhamentoApplicationTests {

    @Test
    void contextoSobe() {
    }
}
