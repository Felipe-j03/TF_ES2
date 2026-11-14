package br.pucrs.es2.notificacoes.persistencia;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class NotificacaoRepository {

    private final JdbcTemplate jdbc;

    public NotificacaoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Insere uma solicitação nova ou reclama atomicamente uma falha cujo prazo de retry chegou. */
    public InicioProcessamento iniciar(String chave, String payloadJson, Instant agora, int maxTentativas) {
        long agoraMs = agora.toEpochMilli();
        try {
            jdbc.update("""
                    INSERT INTO notificacao (idempotency_key, status, tentativas, payload_json,
                                             ultimo_erro, proxima_tentativa_ms, criado_em_ms, atualizado_em_ms)
                    VALUES (?, 'PROCESSANDO', 1, ?, NULL, NULL, ?, ?)
                    """, chave, payloadJson, agoraMs, agoraMs);
            return new InicioProcessamento(true, 1);
        } catch (DuplicateKeyException existente) {
            // A chave já foi processada ou outra instância ganhou a disputa pela solicitação.
        }

        int atualizadas = jdbc.update("""
                UPDATE notificacao
                   SET status = 'PROCESSANDO',
                       tentativas = tentativas + 1,
                       payload_json = ?,
                       ultimo_erro = NULL,
                       proxima_tentativa_ms = NULL,
                       atualizado_em_ms = ?
                 WHERE idempotency_key = ?
                   AND status = 'FALHOU'
                   AND tentativas < ?
                   AND proxima_tentativa_ms IS NOT NULL
                   AND proxima_tentativa_ms <= ?
                """, payloadJson, agoraMs, chave, maxTentativas, agoraMs);
        if (atualizadas == 0) {
            return new InicioProcessamento(false, 0);
        }

        Integer tentativa = jdbc.queryForObject(
                "SELECT tentativas FROM notificacao WHERE idempotency_key = ?", Integer.class, chave);
        return new InicioProcessamento(true, tentativa == null ? 1 : tentativa);
    }

    public void marcarEnviado(String chave, Instant agora) {
        jdbc.update("""
                UPDATE notificacao
                   SET status = 'ENVIADO', payload_json = NULL, ultimo_erro = NULL,
                       proxima_tentativa_ms = NULL, atualizado_em_ms = ?
                 WHERE idempotency_key = ?
                """, agora.toEpochMilli(), chave);
    }

    public void marcarFalha(String chave, String erro, Instant proximaTentativa, Instant agora) {
        if (proximaTentativa == null) {
            jdbc.update("""
                    UPDATE notificacao
                       SET status = 'FALHOU', payload_json = NULL, ultimo_erro = ?,
                           proxima_tentativa_ms = NULL, atualizado_em_ms = ?
                     WHERE idempotency_key = ?
                    """, limitar(erro), agora.toEpochMilli(), chave);
            return;
        }

        jdbc.update("""
                UPDATE notificacao
                   SET status = 'FALHOU', ultimo_erro = ?, proxima_tentativa_ms = ?, atualizado_em_ms = ?
                 WHERE idempotency_key = ?
                """, limitar(erro), proximaTentativa.toEpochMilli(), agora.toEpochMilli(), chave);
    }

    public void marcarFalhaIrrecuperavel(String chave, String erro, Instant agora) {
        jdbc.update("""
                UPDATE notificacao
                   SET status = 'FALHOU', payload_json = NULL, ultimo_erro = ?,
                       proxima_tentativa_ms = NULL, atualizado_em_ms = ?
                 WHERE idempotency_key = ?
                """, limitar(erro), agora.toEpochMilli(), chave);
    }

    public List<NotificacaoPendente> buscarFalhasVencidas(Instant agora, int maxTentativas, int limite) {
        return jdbc.query("""
                SELECT idempotency_key, payload_json, tentativas
                  FROM notificacao
                 WHERE status = 'FALHOU'
                   AND proxima_tentativa_ms IS NOT NULL
                   AND proxima_tentativa_ms <= ?
                   AND tentativas < ?
                 ORDER BY proxima_tentativa_ms
                 LIMIT ?
                """, (rs, rowNum) -> new NotificacaoPendente(
                rs.getString("idempotency_key"), rs.getString("payload_json"), rs.getInt("tentativas")),
                agora.toEpochMilli(), maxTentativas, limite);
    }

    public Optional<EstadoNotificacao> buscarEstado(String chave) {
        List<EstadoNotificacao> encontrados = jdbc.query("""
                SELECT idempotency_key, status, tentativas, ultimo_erro, proxima_tentativa_ms
                  FROM notificacao
                 WHERE idempotency_key = ?
                """, (rs, rowNum) -> {
            long proximaTentativa = rs.getLong("proxima_tentativa_ms");
            Long proxima = rs.wasNull() ? null : proximaTentativa;
            return new EstadoNotificacao(rs.getString("idempotency_key"), rs.getString("status"),
                    rs.getInt("tentativas"), rs.getString("ultimo_erro"), proxima);
        }, chave);
        return encontrados.stream().findFirst();
    }

    private String limitar(String texto) {
        if (texto == null) {
            return "Falha sem mensagem de erro";
        }
        return texto.length() <= 2000 ? texto : texto.substring(0, 2000);
    }
}
