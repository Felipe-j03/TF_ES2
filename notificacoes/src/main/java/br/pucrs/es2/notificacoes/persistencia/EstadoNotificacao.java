package br.pucrs.es2.notificacoes.persistencia;

public record EstadoNotificacao(String idempotencyKey, String status, int tentativas,
                                String ultimoErro, Long proximaTentativaEpochMillis) {
}
