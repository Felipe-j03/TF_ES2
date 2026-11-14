package br.pucrs.es2.notificacoes.persistencia;

public record NotificacaoPendente(String idempotencyKey, String payloadJson, int tentativas) {
}
