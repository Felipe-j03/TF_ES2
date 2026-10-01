package br.pucrs.es2.acompanhamento.moodle;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class MoodleConfig {

    @Bean
    RestClient moodleHttp(MoodleProperties props) {
        return RestClient.builder().baseUrl(props.url()).build();
    }
}
