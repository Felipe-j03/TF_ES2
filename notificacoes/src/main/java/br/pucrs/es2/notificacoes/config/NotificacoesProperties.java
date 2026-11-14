package br.pucrs.es2.notificacoes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "notificacoes")
public record NotificacoesProperties(String remetente, int maxTentativas, Duration intervaloRetentativa) {

    public NotificacoesProperties {
        if (remetente == null || remetente.isBlank()) {
            throw new IllegalArgumentException("notificacoes.remetente deve ser informado");
        }
        if (maxTentativas < 1) {
            throw new IllegalArgumentException("notificacoes.max-tentativas deve ser pelo menos 1");
        }
        if (intervaloRetentativa == null || intervaloRetentativa.isNegative() || intervaloRetentativa.isZero()) {
            throw new IllegalArgumentException("notificacoes.intervalo-retentativa deve ser positivo");
        }
    }
}
