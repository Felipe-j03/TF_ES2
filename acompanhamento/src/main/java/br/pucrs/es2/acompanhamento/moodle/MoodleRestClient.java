package br.pucrs.es2.acompanhamento.moodle;

import br.pucrs.es2.acompanhamento.moodle.dto.Aluno;
import br.pucrs.es2.acompanhamento.moodle.dto.Entrega;
import br.pucrs.es2.acompanhamento.moodle.dto.Tarefa;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Acessa os webservices REST nativos do Moodle. */
@Component
public class MoodleRestClient implements MoodleClient {

    private static final String ENDPOINT = "/webservice/rest/server.php";

    private final RestClient restClient;
    private final MoodleProperties props;
    private final JsonMapper jsonMapper;

    public MoodleRestClient(RestClient moodleHttp, MoodleProperties props, JsonMapper jsonMapper) {
        this.restClient = moodleHttp;
        this.props = props;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public List<Tarefa> buscarTarefas(long cursoId) {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("courseids[0]", String.valueOf(cursoId));
        JsonNode resposta = chamar("mod_assign_get_assignments", params);

        List<Tarefa> tarefas = new ArrayList<>();
        for (JsonNode curso : resposta.path("courses")) {
            for (JsonNode a : curso.path("assignments")) {
                long duedate = a.path("duedate").asLong();
                long cmid = a.path("cmid").asLong();
                tarefas.add(new Tarefa(
                        a.path("id").asLong(),
                        cmid,
                        curso.path("id").asLong(),
                        curso.path("fullname").asString(),
                        a.path("name").asString(),
                        duedate > 0 ? Instant.ofEpochSecond(duedate) : null,
                        a.path("teamsubmission").asInt() == 1,
                        props.urlPublica() + "/mod/assign/view.php?id=" + cmid));
            }
        }
        return tarefas;
    }

    @Override
    public List<Aluno> buscarAlunos(long cursoId) {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("courseid", String.valueOf(cursoId));
        params.add("options[0][name]", "withcapability");
        params.add("options[0][value]", "mod/assign:submit");
        JsonNode resposta = chamar("core_enrol_get_enrolled_users", params);

        List<Aluno> alunos = new ArrayList<>();
        for (JsonNode u : resposta) {
            alunos.add(new Aluno(u.path("id").asLong(), u.path("fullname").asString(), u.path("email").asString()));
        }
        return alunos;
    }

    @Override
    public List<Entrega> buscarEntregas(List<Long> tarefaIds) {
        if (tarefaIds.isEmpty()) {
            return List.of();
        }
        var params = new LinkedMultiValueMap<String, String>();
        for (int i = 0; i < tarefaIds.size(); i++) {
            params.add("assignmentids[" + i + "]", String.valueOf(tarefaIds.get(i)));
        }
        JsonNode resposta = chamar("mod_assign_get_submissions", params);

        List<Entrega> entregas = new ArrayList<>();
        for (JsonNode a : resposta.path("assignments")) {
            long tarefaId = a.path("assignmentid").asLong();
            for (JsonNode s : a.path("submissions")) {
                entregas.add(new Entrega(tarefaId, s.path("userid").asLong(), s.path("status").asString()));
            }
        }
        return entregas;
    }

    private JsonNode chamar(String funcao, MultiValueMap<String, String> params) {
        params.add("wstoken", props.token());
        params.add("wsfunction", funcao);
        params.add("moodlewsrestformat", "json");

        String corpo = restClient.post()
                .uri(ENDPOINT)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(params)
                .retrieve()
                .body(String.class);

        JsonNode json = jsonMapper.readTree(corpo);
        // O Moodle responde HTTP 200 mesmo em caso de erro, com um objeto "exception"
        if (json.isObject() && json.has("exception")) {
            throw new MoodleException(json.path("errorcode").asString(), json.path("message").asString());
        }
        return json;
    }
}
