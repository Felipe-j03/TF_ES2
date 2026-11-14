# Reengajamento de estudantes no Moodle

Trabalho de Engenharia de Software II (PUCRS, 2026/2). O sistema acompanha tarefas de uma disciplina no Moodle e envia um lembrete por e-mail a cada aluno que ainda não entregou uma tarefa individual com prazo nas próximas 24 horas.

## Funcionalidades

- Consulta tarefas, alunos e entregas pelos webservices REST do Moodle.
- Ignora tarefas sem prazo, vencidas, fora da janela de 24 horas ou configuradas para entrega em grupo.
- Não solicita lembrete para aluno cuja entrega esteja com status `submitted`; rascunhos não contam como entrega concluída.
- Publica solicitações JSON no RabbitMQ com uma chave estável por curso, tarefa e aluno.
- Envia e-mails de texto com nome da tarefa, prazo em UTC e link de acesso.
- Registra o estado de cada envio em um banco H2 persistente, não repete envios concluídos e agenda nova tentativa para falhas temporárias.
- Inclui ambiente local com Moodle, PostgreSQL, RabbitMQ e Mailpit.

O escopo é uma disciplina, tarefas individuais visíveis para a turma, prazo único e um lembrete por aluno/tarefa. Interface própria, prorrogações individuais, entregas em grupo e outros canais de contato ficam fora do escopo.

## Componentes

```text
Moodle --REST--> acompanhamento --RabbitMQ--> notificações --SMTP--> Mailpit/e-mail
                     |                              |
                decide quem recebe          registra envio e retries
```

| Diretório | Responsabilidade |
|---|---|
| `acompanhamento/` | Consulta o Moodle, aplica as regras de pendência e publica solicitações. Java 21 e Spring Boot. |
| `notificacoes/` | Consome solicitações, envia o e-mail e registra sucesso/falha e tentativas. Java 21 e Spring Boot. |
| `moodle/` | Prepara o Moodle local e cria usuários, tarefas e entregas fictícias para demonstração. |
| `docs/setup.md` | Requisitos, inicialização do ambiente, testes automatizados e cenários completos de demonstração. |

## Início rápido

1. Copie `.env.example` para `.env`.
2. Inicie Moodle, PostgreSQL, RabbitMQ e Mailpit com `docker compose up -d moodle-db moodle rabbitmq mailpit`.
3. Rode `bash moodle/setup.sh` e copie `MOODLE_TOKEN` e `MOODLE_CURSO_ID` para `.env`.
4. Rode `docker compose up -d --build acompanhamento notificacoes`.
5. Acompanhe `docker compose logs -f acompanhamento notificacoes` e confira os e-mails em [http://localhost:8025](http://localhost:8025).

O passo a passo completo e os resultados esperados de cada cenário estão em [docs/setup.md](docs/setup.md).

## Testes automatizados

Os módulos têm testes independentes e não precisam de Docker:

```bash
mvn -f acompanhamento/pom.xml test
mvn -f notificacoes/pom.xml test
```

Os testes verificam a regra da janela e das entregas, o mapeamento da API Moodle, a publicação da mensagem, deduplicação persistida, retries e conteúdo do e-mail.
