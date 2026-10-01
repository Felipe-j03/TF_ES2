# TF_ES2: reengajamento de estudantes no Moodle

Trabalho final de Engenharia de Software II (PUCRS, 2026/2). Um serviço que consulta o Moodle e manda um lembrete por e-mail para o aluno que ainda não entregou uma tarefa que vence nas próximas 24 horas.

## Como funciona

```
Moodle ──REST──> acompanhamento ──RabbitMQ──> notificações ──SMTP──> e-mail
                 (consulta e decide)          (envia e registra)     (Mailpit nos testes)
```

- **acompanhamento**: de tempos em tempos busca no Moodle as tarefas, os alunos e as entregas da disciplina, decide quem precisa de lembrete e publica uma solicitação na fila.
- **notificações**: consome a fila, envia o e-mail e registra o resultado, sem mandar duas vezes o mesmo lembrete.

O Moodle é usado como está. A integração usa só três funções dos webservices nativos: `mod_assign_get_assignments`, `core_enrol_get_enrolled_users` e `mod_assign_get_submissions`.

## Estrutura

```
docker-compose.yml   Moodle, PostgreSQL, RabbitMQ, Mailpit e o acompanhamento
.env.example         variáveis do ambiente (copiar para .env)
moodle/              script que configura o Moodle local e cria dados de teste
acompanhamento/      microsserviço Spring Boot (Java 21)
notificacoes/        microsserviço de notificações (a fazer)
docs/setup.md        guia de instalação e uso
```

## Começando

O passo a passo completo está em [docs/setup.md](docs/setup.md). Resumo:

```bash
cp .env.example .env
docker compose up -d moodle-db moodle rabbitmq mailpit
bash moodle/setup.sh            # copiar MOODLE_TOKEN e MOODLE_CURSO_ID para o .env
docker compose up -d --build acompanhamento
```

## Situação

| Parte | Estado |
|---|---|
| Ambiente Docker e dados de teste | pronto |
| acompanhamento: cliente do Moodle, agendador, publicação na fila | pronto, com testes |
| acompanhamento: regra de quem recebe lembrete (`DetectorPendencias`) | a fazer; os testes em `DetectorPendenciasTest` descrevem a regra e estão com `@Disabled` |
| notificações | a fazer; contrato da mensagem em [notificacoes/README.md](notificacoes/README.md) |
