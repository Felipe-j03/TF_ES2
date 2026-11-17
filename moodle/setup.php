<?php
// Configura o Moodle local para o projeto de reengajamento.
// Usa apenas a API interna do Moodle; não altera código nem instala plugins.
// Pode ser executado várias vezes: o que já existe é reaproveitado.

define('CLI_SCRIPT', true);
require('/var/www/html/config.php');
require_once($CFG->libdir . '/clilib.php');
require_once($CFG->libdir . '/testing/generator/lib.php');
require_once($CFG->dirroot . '/mod/assign/locallib.php');

$generator = testing_util::get_data_generator();
\core\session\manager::set_user(get_admin());

// O Moodle local não envia e-mails próprios (boas-vindas etc.); os lembretes são enviados pelo serviço de notificações
set_config('noemailever', 1);

// 1. Webservices + REST
set_config('enablewebservices', 1);
$protocolos = array_filter(explode(',', get_config('core', 'webserviceprotocols') ?: ''));
if (!in_array('rest', $protocolos)) {
    $protocolos[] = 'rest';
    set_config('webserviceprotocols', implode(',', $protocolos));
}
cli_writeln('Webservices REST habilitados');

// 2. Usuário de serviço
$wsuser = $DB->get_record('user', ['username' => 'ws_reengajamento', 'deleted' => 0]);
if (!$wsuser) {
    $wsuser = $generator->create_user([
        'username' => 'ws_reengajamento',
        'firstname' => 'Servico',
        'lastname' => 'Reengajamento',
        'email' => 'ws_reengajamento@example.com',
    ]);
}

// Papel de sistema que permite usar o protocolo REST
$roleid = $DB->get_field('role', 'id', ['shortname' => 'wsreengajamento']);
if (!$roleid) {
    $roleid = create_role('Webservice reengajamento', 'wsreengajamento', 'Acesso REST do serviço de reengajamento');
    set_role_contextlevels($roleid, [CONTEXT_SYSTEM]);
}
$syscontext = context_system::instance();
assign_capability('webservice/rest:use', CAP_ALLOW, $roleid, $syscontext->id, true);
role_assign($roleid, $wsuser->id, $syscontext->id);

// 3. Serviço externo com as três funções
$funcoes = ['mod_assign_get_assignments', 'core_enrol_get_enrolled_users', 'mod_assign_get_submissions'];
$service = $DB->get_record('external_services', ['shortname' => 'reengajamento']);
if (!$service) {
    $service = (object) [
        'name' => 'Reengajamento',
        'shortname' => 'reengajamento',
        'enabled' => 1,
        'restrictedusers' => 1,
        'downloadfiles' => 0,
        'uploadfiles' => 0,
        'timecreated' => time(),
    ];
    $service->id = $DB->insert_record('external_services', $service);
}
foreach ($funcoes as $f) {
    if (!$DB->record_exists('external_services_functions', ['externalserviceid' => $service->id, 'functionname' => $f])) {
        $DB->insert_record('external_services_functions', ['externalserviceid' => $service->id, 'functionname' => $f]);
    }
}
if (!$DB->record_exists('external_services_users', ['externalserviceid' => $service->id, 'userid' => $wsuser->id])) {
    $DB->insert_record('external_services_users', [
        'externalserviceid' => $service->id, 'userid' => $wsuser->id, 'timecreated' => time(),
    ]);
}
$token = $DB->get_field('external_tokens', 'token', ['externalserviceid' => $service->id, 'userid' => $wsuser->id]);
if (!$token) {
    $token = \core_external\util::generate_token(EXTERNAL_TOKEN_PERMANENT, $service, $wsuser->id, $syscontext);
}
// Se WS_TOKEN estiver definido (docker compose), o token passa a ter esse valor fixo,
// para o acompanhamento não depender de copiar um token gerado aleatoriamente.
$tokenfixo = getenv('WS_TOKEN');
if ($tokenfixo !== false && $tokenfixo !== '' && $tokenfixo !== $token) {
    $DB->set_field('external_tokens', 'token', $tokenfixo,
        ['externalserviceid' => $service->id, 'userid' => $wsuser->id]);
    $token = $tokenfixo;
}
cli_writeln('Serviço externo e token prontos');

// 4. Disciplina e matrículas
$course = $DB->get_record('course', ['shortname' => 'ES2DEMO']);
if (!$course) {
    $course = $generator->create_course(['fullname' => 'ES2 - Demonstração', 'shortname' => 'ES2DEMO']);
}
$generator->enrol_user($wsuser->id, $course->id, 'editingteacher');

$alunos = [];
foreach ([1, 2, 3, 4] as $n) {
    $u = $DB->get_record('user', ['username' => "aluno$n", 'deleted' => 0]);
    if (!$u) {
        $u = $generator->create_user([
            'username' => "aluno$n", 'firstname' => 'Aluno', 'lastname' => "Teste $n",
            'email' => "aluno$n@example.com",
            'password' => 'Aluno123!',
        ]);
    } else {
        // Contas fictícias do ambiente local: senha conhecida para entrar como aluno na demonstração
        update_internal_user_password($u, 'Aluno123!');
    }
    $generator->enrol_user($u->id, $course->id, 'student');
    $alunos[$n] = $u;
}
cli_writeln('Disciplina e alunos prontos');

// 5. Tarefas de teste (critérios de aceite)
$agora = time();
$tarefas = [
    'Tarefa pendente (vence em 2h)'    => [$agora + 2 * HOURSECS, false],
    'Tarefa fora da janela (3 dias)'   => [$agora + 3 * DAYSECS, false],
    'Tarefa sem prazo'                 => [0, false],
    'Tarefa já vencida'                => [$agora - DAYSECS, false],
    'Tarefa com entrega (vence em 2h)' => [$agora + 2 * HOURSECS, false],
    'Tarefa em grupo (vence em 2h)'    => [$agora + 2 * HOURSECS, true],
];
foreach ($tarefas as $nome => [$duedate, $emGrupo]) {
    $existente = $DB->get_record('assign', ['course' => $course->id, 'name' => $nome]);
    if ($existente) {
        // Mantém os prazos relativos a "agora" a cada execução
        $DB->set_field('assign', 'duedate', $duedate, ['id' => $existente->id]);
        $DB->set_field('assign', 'teamsubmission', $emGrupo ? 1 : 0, ['id' => $existente->id]);
        continue;
    }
    $generator->create_module('assign', [
        'course' => $course->id,
        'name' => $nome,
        'duedate' => $duedate,
        'allowsubmissionsfromdate' => 0,
        'cutoffdate' => 0,
        'gradingduedate' => 0,
        'submissiondrafts' => 0,
        'teamsubmission' => $emGrupo ? 1 : 0,
        'assignsubmission_onlinetext_enabled' => 1,
    ]);
}

// Entrega do aluno1 na tarefa com entrega
$assignrec = $DB->get_record('assign', ['course' => $course->id, 'name' => 'Tarefa com entrega (vence em 2h)'], '*', MUST_EXIST);
$cm = get_coursemodule_from_instance('assign', $assignrec->id, $course->id, false, MUST_EXIST);
$assign = new assign(context_module::instance($cm->id), $cm, $course);
$submission = $assign->get_user_submission($alunos[1]->id, true);
if ($submission->status !== ASSIGN_SUBMISSION_STATUS_SUBMITTED) {
    $submission->status = ASSIGN_SUBMISSION_STATUS_SUBMITTED;
    $submission->timemodified = time();
    $DB->update_record('assign_submission', $submission);
}
rebuild_course_cache($course->id, true);
cli_writeln('Tarefas de teste prontas');

cli_writeln('');
cli_writeln('Copie para o .env:');
cli_writeln("MOODLE_TOKEN=$token");
cli_writeln("MOODLE_CURSO_ID={$course->id}");
