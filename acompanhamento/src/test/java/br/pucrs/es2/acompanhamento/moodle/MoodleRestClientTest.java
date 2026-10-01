package br.pucrs.es2.acompanhamento.moodle;

import br.pucrs.es2.acompanhamento.moodle.dto.Aluno;
import br.pucrs.es2.acompanhamento.moodle.dto.Entrega;
import br.pucrs.es2.acompanhamento.moodle.dto.Tarefa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MoodleRestClientTest {

    private static final String ENDPOINT = "http://moodle.test/webservice/rest/server.php";

    private MockRestServiceServer server;
    private MoodleRestClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://moodle.test");
        server = MockRestServiceServer.bindTo(builder).build();
        var props = new MoodleProperties("http://moodle.test", "http://localhost:8080", "tok", 2);
        client = new MoodleRestClient(builder.build(), props, JsonMapper.builder().build());
    }

    @Test
    void buscarTarefasMapeiaPrazoLinkECurso() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(content().formDataContains(Map.of(
                        "wstoken", "tok",
                        "wsfunction", "mod_assign_get_assignments",
                        "moodlewsrestformat", "json",
                        "courseids[0]", "2")))
                .andRespond(withSuccess("""
                        {"courses":[{"id":2,"fullname":"ES2 - Demonstração","assignments":[
                          {"id":10,"cmid":55,"course":2,"name":"T1","duedate":1790000000,"teamsubmission":0},
                          {"id":11,"cmid":56,"course":2,"name":"T2","duedate":0,"teamsubmission":1}
                        ]}],"warnings":[]}
                        """, MediaType.APPLICATION_JSON));

        List<Tarefa> tarefas = client.buscarTarefas(2);

        assertThat(tarefas).containsExactly(
                new Tarefa(10, 55, 2, "ES2 - Demonstração", "T1", Instant.ofEpochSecond(1790000000L), false,
                        "http://localhost:8080/mod/assign/view.php?id=55"),
                new Tarefa(11, 56, 2, "ES2 - Demonstração", "T2", null, true,
                        "http://localhost:8080/mod/assign/view.php?id=56"));
    }

    @Test
    void buscarAlunosFiltraPorPermissaoDeEnvio() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(content().formDataContains(Map.of(
                        "wsfunction", "core_enrol_get_enrolled_users",
                        "courseid", "2",
                        "options[0][name]", "withcapability",
                        "options[0][value]", "mod/assign:submit")))
                .andRespond(withSuccess("""
                        [{"id":7,"fullname":"Aluno Teste 1","email":"aluno1@example.com","roles":[]}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.buscarAlunos(2))
                .containsExactly(new Aluno(7, "Aluno Teste 1", "aluno1@example.com"));
    }

    @Test
    void buscarEntregasJuntaTodasAsTarefas() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(content().formDataContains(Map.of(
                        "wsfunction", "mod_assign_get_submissions",
                        "assignmentids[0]", "10",
                        "assignmentids[1]", "11")))
                .andRespond(withSuccess("""
                        {"assignments":[
                          {"assignmentid":10,"submissions":[{"id":1,"userid":7,"status":"submitted"}]},
                          {"assignmentid":11,"submissions":[{"id":2,"userid":8,"status":"new"}]}
                        ],"warnings":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.buscarEntregas(List.of(10L, 11L))).containsExactly(
                new Entrega(10, 7, "submitted"),
                new Entrega(11, 8, "new"));
    }

    @Test
    void buscarEntregasSemTarefasNaoChamaOMoodle() {
        assertThat(client.buscarEntregas(List.of())).isEmpty();
        server.verify();
    }

    @Test
    void erroDoMoodleViraMoodleException() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("""
                        {"exception":"moodle_exception","errorcode":"invalidtoken","message":"Invalid token"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.buscarAlunos(2))
                .isInstanceOf(MoodleException.class)
                .hasMessageContaining("invalidtoken");
    }
}
