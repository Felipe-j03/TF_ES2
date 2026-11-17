package br.pucrs.es2.notificacoes.email;

import br.pucrs.es2.notificacoes.config.NotificacoesProperties;
import br.pucrs.es2.notificacoes.mensageria.SolicitacaoReengajamento;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class SmtpNotificadorTest {

    @Test
    void montaLembreteComTarefaPrazoLinkEAluno() {
        JavaMailSender sender = mock(JavaMailSender.class);
        var notificador = new SmtpNotificador(sender,
                new NotificacoesProperties("noreply@example.com", 5, java.time.Duration.ofSeconds(30)));
        var solicitacao = new SolicitacaoReengajamento("2:10:7", 2, "ES2", 10, "T1",
                Instant.parse("2026-10-01T14:00:00Z"), "http://localhost/tarefa", 7, "Ana",
                "ana@example.com", Instant.parse("2026-10-01T12:00:00Z"));
        ArgumentCaptor<SimpleMailMessage> mensagem = ArgumentCaptor.forClass(SimpleMailMessage.class);

        notificador.enviar(solicitacao);

        verify(sender).send(mensagem.capture());
        SimpleMailMessage email = mensagem.getValue();
        assertThat(email.getFrom()).isEqualTo("noreply@example.com");
        assertThat(email.getTo()).containsExactly("ana@example.com");
        assertThat(email.getSubject()).contains("T1");
        assertThat(email.getText()).contains("ES2", "01/10/2026 às 11:00 (Brasília)", "http://localhost/tarefa");
    }
}
