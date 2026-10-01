# Guia de instalação e uso

## Pré-requisitos

- Docker Desktop. O Moodle sozinho usa perto de 1,5 GB de RAM; se o Docker tiver só 4 GB, desligue outros projetos enquanto trabalha neste.
- Git Bash (vem com o Git para Windows), para rodar o script `.sh`.
- Para desenvolver o microsserviço fora do Docker: JDK 21 e Maven 3.9.

## 1. Subir o ambiente

```bash
cp .env.example .env
docker compose up -d moodle-db moodle rabbitmq mailpit
docker compose logs -f moodle
```

Na primeira vez o Moodle instala o banco, o que leva alguns minutos. Pode sair do `logs -f` (Ctrl+C) quando aparecer `ready to handle connections`.

| Serviço | Endereço | Login |
|---|---|---|
| Moodle | http://localhost:8080 | `admin` / `Admin123!` |
| RabbitMQ (painel) | http://localhost:15672 | `reengajamento` / `reengajamento` |
| Mailpit (caixa de e-mail de teste) | http://localhost:8025 | sem login |

Se a porta 5672 ou 15672 já estiver em uso na sua máquina, mude `RABBITMQ_PORT` e `RABBITMQ_UI_PORT` no `.env`.

## 2. Configurar o Moodle local

```bash
bash moodle/setup.sh
```

O script roda dentro do contêiner do Moodle e usa só a API interna dele. Nenhum plugin é instalado e nenhum arquivo do Moodle é alterado. Ele:

- habilita os webservices e o protocolo REST;
- cria o usuário `ws_reengajamento`, matriculado como professor na disciplina, e o serviço externo "Reengajamento" com as três funções que usamos;
- cria a disciplina `ES2DEMO` com os alunos `aluno1` a `aluno4` (e-mails `@example.com`);
- cria cinco tarefas, uma para cada situação dos critérios de aceite.

| Tarefa | Prazo | Lembrete esperado |
|---|---|---|
| Tarefa pendente (vence em 2h) | daqui a 2 horas | sim, para os 4 alunos |
| Tarefa fora da janela (3 dias) | daqui a 3 dias | não |
| Tarefa sem prazo | nenhum | não |
| Tarefa já vencida | ontem | não |
| Tarefa com entrega (vence em 2h) | daqui a 2 horas | sim, menos para o aluno1, que já entregou |

No final ele imprime duas linhas. Copie as duas para o `.env`:

```
MOODLE_TOKEN=...
MOODLE_CURSO_ID=2
```

Pode rodar o script de novo quando quiser. Nada é duplicado, o token continua o mesmo e os prazos são recalculados a partir da hora atual, o que é útil porque a tarefa "vence em 2h" deixa de estar na janela no dia seguinte.

O Moodle local fica com o envio de e-mails próprios desligado (`noemailever`). Os lembretes do projeto saem pelo serviço de notificações, não pelo Moodle.

## 3. Rodar o acompanhamento

### No Docker

```bash
docker compose up -d --build acompanhamento
docker compose logs -f acompanhamento
```

Com `ACOMPANHAMENTO_INTERVALO=PT1M` no `.env`, a verificação roda a cada minuto (o padrão é `PT1H`). Cada execução gera uma linha assim:

```
Verificação do curso 2: 5 tarefas, 4 alunos, 8 entregas, 0 solicitações publicadas
```

Enquanto o `DetectorPendencias` não for implementado, o número de solicitações fica em 0.

### Pela IDE ou terminal

Pare o contêiner (`docker compose stop acompanhamento`) e rode na pasta `acompanhamento/`:

```bash
export MOODLE_TOKEN=<token> MOODLE_CURSO_ID=2 RABBITMQ_PORT=5672 SERVER_PORT=8082 ACOMPANHAMENTO_INTERVALO=PT1M
mvn spring-boot:run
```

O `SERVER_PORT=8082` é necessário porque a 8081 continua reservada pelo contêiner do Moodle mesmo com o acompanhamento parado (veja a seção sobre a rede, mais abaixo). Use o mesmo valor de `RABBITMQ_PORT` que estiver no seu `.env`.

No IntelliJ, as mesmas variáveis vão em Run Configuration → Environment variables. Os testes rodam com `mvn test` e não precisam do Docker.

## Por que o acompanhamento usa a rede do contêiner do Moodle

O Moodle só aceita requisições feitas pelo endereço configurado como `SITE_URL` (`http://localhost:8080`). Chamado por outro nome, como `http://moodle:8080`, ele responde com um redirecionamento para `localhost:8080`, e dentro de um contêiner comum `localhost` é o próprio contêiner.

Por isso o serviço `acompanhamento` usa `network_mode: service:moodle` no `docker-compose.yml`: ele compartilha a rede do contêiner do Moodle e acessa `localhost:8080` como faria rodando direto na máquina. A porta 8081 do acompanhamento aparece publicada no serviço `moodle` por causa disso.

Testamos também o `$CFG->reverseproxy` do Moodle, mas com ele ativo o Moodle bloqueia o acesso direto pelo navegador.

Isso só afeta o ambiente local. Em uma instalação real o Moodle tem um endereço público, e basta apontar `MOODLE_URL` para ele.

## Configurar em um Moodle real

Os passos equivalentes ao script, feitos pela interface por um administrador:

1. Administração do site → Recursos avançados: ativar "Habilitar serviços web".
2. Administração do site → Servidor → Serviços web → Gerenciar protocolos: habilitar REST.
3. Criar um usuário para o serviço e dar a ele, em um papel de sistema, a permissão `webservice/rest:use`.
4. Serviços web → Serviços externos: criar um serviço com as funções `mod_assign_get_assignments`, `core_enrol_get_enrolled_users` e `mod_assign_get_submissions`, marcando "Somente usuários autorizados" e autorizando o usuário criado.
5. Serviços web → Gerenciar tokens: gerar um token para esse usuário e esse serviço.
6. Matricular o usuário do serviço como professor na disciplina acompanhada. É isso que dá acesso às entregas e aos e-mails dos alunos.
7. Conferir em Administração → Usuários → Permissões → Políticas do usuário se `showuseridentity` inclui o e-mail.

## Problemas comuns

- **Página do Moodle não abre logo depois do `up`:** a primeira inicialização demora. Acompanhe com `docker compose logs -f moodle`.
- **Processos do PHP morrendo com `SIGKILL` nos logs:** falta memória no Docker. Pare outros contêineres.
- **`invalidtoken` no log do acompanhamento:** o token do `.env` não bate com o do Moodle. Rode `bash moodle/setup.sh` e copie o token de novo.
- **Alunos sem e-mail na resposta:** veja o passo 7 da seção anterior.
- **Começar do zero:** `docker compose down -v` apaga os contêineres e os volumes (banco e dados do Moodle).
