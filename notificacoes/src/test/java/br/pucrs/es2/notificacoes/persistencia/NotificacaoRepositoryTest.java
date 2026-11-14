package br.pucrs.es2.notificacoes.persistencia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificacaoRepositoryTest {

    private NotificacaoRepository repository;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        repository = new NotificacaoRepository(new JdbcTemplate(dataSource));
    }

    @Test
    void chaveEnviadaNaoPodeSerReclamadaNovamente() {
        Instant agora = Instant.parse("2026-10-01T12:00:00Z");
        var inicio = repository.iniciar("2:10:7", "{}", agora, 5);
        repository.marcarEnviado("2:10:7", agora);

        var repeticao = repository.iniciar("2:10:7", "{}", agora.plusSeconds(60), 5);

        assertThat(inicio).isEqualTo(new InicioProcessamento(true, 1));
        assertThat(repeticao.iniciado()).isFalse();
        assertThat(repository.buscarEstado("2:10:7")).get()
                .extracting(EstadoNotificacao::status, EstadoNotificacao::tentativas)
                .containsExactly("ENVIADO", 1);
    }

    @Test
    void falhaSoPodeSerReclamadaQuandoChegaOHorarioDaNovaTentativa() {
        Instant agora = Instant.parse("2026-10-01T12:00:00Z");
        Instant tentarNovamente = agora.plusSeconds(30);
        repository.iniciar("2:10:7", "{}", agora, 5);
        repository.marcarFalha("2:10:7", "SMTP indisponível", tentarNovamente, agora);

        var antesDoPrazo = repository.iniciar("2:10:7", "{}", tentarNovamente.minusMillis(1), 5);
        var noPrazo = repository.iniciar("2:10:7", "{}", tentarNovamente, 5);

        assertThat(antesDoPrazo.iniciado()).isFalse();
        assertThat(noPrazo).isEqualTo(new InicioProcessamento(true, 2));
    }

    @Test
    void falhaSemNovaTentativaNaoEntraNaFilaDeRetentativas() {
        Instant agora = Instant.parse("2026-10-01T12:00:00Z");
        repository.iniciar("2:10:7", "{}", agora, 1);
        repository.marcarFalha("2:10:7", "SMTP indisponível", null, agora);

        assertThat(repository.buscarFalhasVencidas(agora.plusSeconds(3600), 1, 100)).isEmpty();
    }
}
