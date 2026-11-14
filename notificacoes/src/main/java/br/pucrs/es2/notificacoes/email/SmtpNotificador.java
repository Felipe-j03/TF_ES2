package br.pucrs.es2.notificacoes.email;

import br.pucrs.es2.notificacoes.config.NotificacoesProperties;
import br.pucrs.es2.notificacoes.mensageria.SolicitacaoReengajamento;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class SmtpNotificador {

    private static final DateTimeFormatter PRAZO_FORMATADO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final JavaMailSender mailSender;
    private final NotificacoesProperties props;

    public SmtpNotificador(JavaMailSender mailSender, NotificacoesProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    public void enviar(SolicitacaoReengajamento solicitacao) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(props.remetente());
        mensagem.setTo(solicitacao.alunoEmail());
        mensagem.setSubject("Lembrete: " + umaLinha(solicitacao.tarefaNome()));
        mensagem.setText(corpo(solicitacao));
        mailSender.send(mensagem);
    }

    private String corpo(SolicitacaoReengajamento solicitacao) {
        String prazo = solicitacao.prazo() == null ? "não informado" : PRAZO_FORMATADO.format(solicitacao.prazo());
        return "Olá, " + texto(solicitacao.alunoNome()) + ".\n\n"
                + "A tarefa \"" + texto(solicitacao.tarefaNome()) + "\" do curso \""
                + texto(solicitacao.cursoNome()) + "\" ainda está pendente.\n"
                + "Prazo: " + prazo + ".\n\n"
                + "Acesse a tarefa: " + texto(solicitacao.linkTarefa()) + "\n\n"
                + "Este é um lembrete automático de reengajamento.";
    }

    private String umaLinha(String valor) {
        return texto(valor).replace('\r', ' ').replace('\n', ' ');
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }
}
