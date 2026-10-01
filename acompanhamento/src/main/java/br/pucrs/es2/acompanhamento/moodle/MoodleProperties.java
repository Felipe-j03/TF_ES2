package br.pucrs.es2.acompanhamento.moodle;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param url        endereço usado pelo serviço para chamar o Moodle
 * @param urlPublica endereço usado nos links enviados aos alunos
 */
@ConfigurationProperties(prefix = "moodle")
public record MoodleProperties(String url, String urlPublica, String token, long cursoId) {
}
