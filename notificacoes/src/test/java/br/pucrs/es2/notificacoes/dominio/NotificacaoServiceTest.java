package br.pucrs.es2.notificacoes.dominio;

import br.pucrs.es2.notificacoes.config.NotificacoesProperties;
import br.pucrs.es2.notificacoes.email.SmtpNotificador;
import br.pucrs.es2.notificacoes.mensageria.SolicitacaoReengajamento;
import br.pucrs.es2.notificacoes.persistencia.InicioProcessamento;
import br.pucrs.es2.notificacoes.persistencia.NotificacaoPendente;
import br.pucrs.es2.notificacoes.persistencia.NotificacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacaoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-01T12:00:00Z");
    private static final String CHAVE = "2:10:7";
    private static final SolicitacaoReengajamento SOLICITACAO = new SolicitacaoReengajamento(
            CHAVE, 2, "ES2", 10, "T1", Instant.parse("2026-10-01T14:00:00Z"),
            "http://localhost/tarefa", 7, "Ana", "ana@example.com", AGORA);

    @Mock
    private NotificacaoRepository repository;
    @Mock
    private SmtpNotificador notificador;

    private JsonMapper jsonMapper;
    private NotificacaoService service;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        service = new NotificacaoService(repository, notificador, jsonMapper,
                new NotificacoesProperties("noreply@example.com", 3, Duration.ofSeconds(30)),
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void enviaMensagemNovaERegistraSucesso() {
        when(repository.iniciar(eq(CHAVE), anyString(), eq(AGORA), eq(3)))
                .thenReturn(new InicioProcessamento(true, 1));

        service.processar(SOLICITACAO);

        verify(notificador).enviar(SOLICITACAO);
        verify(repository).marcarEnviado(CHAVE, AGORA);
    }

    @Test
    void ignoraSolicitacaoJaConcluidaSemEnviarDeNovo() {
        when(repository.iniciar(eq(CHAVE), anyString(), eq(AGORA), eq(3)))
                .thenReturn(new InicioProcessamento(false, 0));

        service.processar(SOLICITACAO);

        verify(notificador, never()).enviar(SOLICITACAO);
        verify(repository, never()).marcarEnviado(eq(CHAVE), eq(AGORA));
    }

    @Test
    void registraFalhaEAgendaNovaTentativa() {
        when(repository.iniciar(eq(CHAVE), anyString(), eq(AGORA), eq(3)))
                .thenReturn(new InicioProcessamento(true, 1));
        doThrow(new IllegalStateException("SMTP indisponível")).when(notificador).enviar(SOLICITACAO);

        service.processar(SOLICITACAO);

        verify(repository).marcarFalha(eq(CHAVE), eq("IllegalStateException: SMTP indisponível"),
                eq(AGORA.plusSeconds(30)), eq(AGORA));
        verify(repository, never()).marcarEnviado(CHAVE, AGORA);
    }

    @Test
    void falhaNaUltimaTentativaNaoAgendaOutroRetry() {
        when(repository.iniciar(eq(CHAVE), anyString(), eq(AGORA), eq(3)))
                .thenReturn(new InicioProcessamento(true, 3));
        doThrow(new IllegalStateException("SMTP indisponível")).when(notificador).enviar(SOLICITACAO);

        service.processar(SOLICITACAO);

        verify(repository).marcarFalha(eq(CHAVE), eq("IllegalStateException: SMTP indisponível"),
                isNull(), eq(AGORA));
    }

    @Test
    void reprocessaFalhaVencidaEMarcaEnvioConcluido() {
        String payload = jsonMapper.writeValueAsString(SOLICITACAO);
        when(repository.buscarFalhasVencidas(AGORA, 3, 100))
                .thenReturn(List.of(new NotificacaoPendente(CHAVE, payload, 1)));
        when(repository.iniciar(eq(CHAVE), anyString(), eq(AGORA), eq(3)))
                .thenReturn(new InicioProcessamento(true, 2));

        service.reprocessarFalhasVencidas();

        verify(notificador).enviar(SOLICITACAO);
        verify(repository).marcarEnviado(CHAVE, AGORA);
    }
}
