# Guia completo: requisitos, execução e testes

Este guia cobre o fluxo inteiro: Moodle → acompanhamento → RabbitMQ → notificações → SMTP/Mailpit. O ambiente local usa apenas contas fictícias e não envia e-mails para alunos reais.

## 1. Requisitos

### Para rodar o sistema completo

- Docker Desktop ou Docker Engine com o plugin Docker Compose.
- Pelo menos 4 GB de memória disponíveis para o Docker; o Moodle sozinho pode usar cerca de 1,5 GB.
- Git Bash no Windows, apenas se quiser rodar `moodle/setup.sh` para renovar os prazos de teste.

### Para rodar os testes automatizados ou executar um serviço fora do Docker

- JDK 21.
- Maven 3.9 ou superior.

Os testes automatizados usam dependências locais de teste e não precisam que Moodle, RabbitMQ ou Mailpit estejam ativos.

## 2. Preparar a configuração

Na raiz do repositório, copie o arquivo de exemplo:

```bash
cp .env.example .env
```

Para a demonstração, use `ACOMPANHAMENTO_INTERVALO=PT1M`; `PT1H` executa a consulta a cada hora. Os prazos das tarefas fictícias são calculados a partir do horário em que o setup do Moodle roda.

O Mailpit é o SMTP local: `SMTP_HOST=mailpit`, `SMTP_PORT=1025`, sem autenticação. Os e-mails aparecem em [http://localhost:8025](http://localhost:8025). O serviço não usa o SMTP próprio do Moodle.

## 3. Subir tudo com um comando

Na raiz do projeto:

```bash
docker compose up -d --build
docker compose logs -f moodle
```

Na primeira inicialização, o Moodle instala o banco e em seguida executa automaticamente o `moodle/setup.php` (variável `POST_CONFIGURE_COMMANDS` da imagem). Isso leva alguns minutos. O contêiner do Moodle só passa a `healthy` depois do setup, e os serviços `acompanhamento` e `notificacoes` só iniciam depois disso. Acompanhe com `docker compose ps`.

| Componente | Endereço | Acesso local de demonstração |
|---|---|---|
| Moodle | [http://localhost:8080](http://localhost:8080) | Usuário e senha definidos no `.env` |
| RabbitMQ | [http://localhost:15672](http://localhost:15672) | Usuário e senha definidos no `.env` |
| Mailpit | [http://localhost:8025](http://localhost:8025) | Sem login |

As portas do RabbitMQ podem ser alteradas em `RABBITMQ_PORT` e `RABBITMQ_UI_PORT` no `.env`.

O Moodle é instalado com a interface em português (`MOODLE_LANGUAGE: pt_br` no `docker-compose.yml`), o que exige acesso à internet na primeira instalação para baixar o pacote de idioma. Em um Moodle já instalado, o idioma muda em *Administração do site → Idioma → Pacotes de idiomas*.

## 4. O que o setup do Moodle cria

O `moodle/setup.php` habilita REST, cria a conta de serviço e o serviço externo com as três funções, define o token a partir de `MOODLE_TOKEN`, matricula quatro alunos fictícios e prepara seis tarefas. Ele é idempotente: reaproveita os registros e **atualiza os prazos relativos ao momento da execução**, o que acontece a cada vez que o contêiner do Moodle inicia. Para renovar os prazos manualmente, rode `bash moodle/setup.sh` (Git Bash no Windows).

O token é local e fixo (`MOODLE_TOKEN`, valor padrão `reengajamento-local-token-es2-01`) e a disciplina de demonstração tem id 2 (`MOODLE_CURSO_ID`). Não use esses valores fora do ambiente local.

Os alunos fictícios (`aluno1` a `aluno4`) têm a senha `Aluno123!`, para entrar em http://localhost:8080 como aluno e ver as tarefas na própria conta. Também é valor apenas local.

| Tarefa preparada | Resultado esperado |
|---|---|
| Pendente, vence em 2 horas | Um lembrete para cada um dos quatro alunos |
| Fora da janela, vence em 3 dias | Nenhum lembrete |
| Sem prazo | Nenhum lembrete |
| Já vencida | Nenhum lembrete |
| Vence em 2 horas, com entrega do aluno1 | Três lembretes; o aluno1 não recebe |
| Em grupo, vence em 2 horas | Nenhum lembrete |

## 5. Acompanhar o fluxo completo

```bash
docker compose logs -f acompanhamento notificacoes
```

O acompanhamento espera 10 segundos antes da primeira verificação e depois usa o intervalo configurado (`PT1M` na demonstração; defina `PT1H` no `.env` para o uso real). A primeira verificação deverá publicar solicitações em até alguns segundos. As demais verificações reapresentam a mesma chave por aluno/tarefa; o serviço de notificações ignora as que já foram enviadas.

Abra [http://localhost:8025](http://localhost:8025). Com o Moodle recém-configurado, devem chegar **7 e-mails**: quatro da tarefa pendente e três da tarefa com entrega. Confira nome da tarefa, prazo em UTC e link no corpo da mensagem. As outras quatro combinações/tarefas de teste não devem gerar e-mail.

O acompanhamento usa `network_mode: service:moodle` para alcançar o Moodle no endereço local configurado por ele. O notificações roda na rede padrão do Compose e alcança RabbitMQ e Mailpit pelos nomes `rabbitmq` e `mailpit`.

## 6. Como verificar cada funcionalidade

### Regra das 24 horas, status de entrega e tarefas individuais

O script de demonstração cobre tarefas dentro e fora da janela, sem prazo, vencida, com uma entrega já enviada e em grupo. Compare a tabela da seção 4 com a caixa do Mailpit. O detector considera válido um prazo estritamente futuro e inclui o instante exato de `agora + 24h`. Um rascunho não é tratado como entrega concluída.

### Integração com os webservices do Moodle

Os testes automatizados de `MoodleRestClientTest` conferem os nomes das três funções REST, os parâmetros, o mapeamento de prazo/link/curso e a conversão da resposta de erro do Moodle. O teste do scheduler cobre a busca conjunta e a publicação. No fluxo manual, confirme nos logs do acompanhamento a contagem de tarefas, alunos, entregas e solicitações.

### Chave de idempotência e persistência

Deixe o acompanhamento completar pelo menos duas verificações, ou reinicie os serviços sem remover os volumes:

```bash
docker compose restart acompanhamento notificacoes
```

O Mailpit deve continuar com os mesmos sete e-mails. Nos logs do notificações, as solicitações repetidas aparecem como ignoradas. O estado `ENVIADO` e a chave ficam no banco H2 do volume `notificacoes-data`; o conteúdo com dados do aluno é apagado do registro após o sucesso.

### Registro de falha e nova tentativa SMTP

Faça este cenário em um ambiente de demonstração novo, antes de as solicitações serem concluídas: `docker compose down -v` apaga os dados locais do Moodle e do notificações. Depois suba só a base, deixe o Moodle ficar saudável e interrompa o SMTP antes de iniciar o acompanhamento:

```bash
docker compose down -v
docker compose up -d --build --wait moodle rabbitmq mailpit notificacoes
docker compose stop mailpit
docker compose up -d --build acompanhamento
docker compose logs -f notificacoes
```

O log deve registrar falha de envio e o número da tentativa. Inicie o SMTP novamente:

```bash
docker compose start mailpit
```

Após o intervalo de retry configurado (`NOTIFICACOES_INTERVALO_RETENTATIVA`, 30 segundos por padrão), o scheduler deve reenviar as solicitações falhadas e os e-mails devem aparecer no Mailpit. O padrão permite até cinco tentativas no total: o primeiro envio e até quatro retries. Ao atingir o limite, a falha permanece registrada sem nova tentativa automática.

## 7. Testes automatizados

Na raiz do projeto, execute separadamente:

```bash
mvn -f acompanhamento/pom.xml test
mvn -f notificacoes/pom.xml test
```

O módulo `acompanhamento` cobre:

- `DetectorPendenciasTest`: prazo dentro/fora da janela, prazo ausente ou vencido, tarefas em grupo, entregas `submitted`, rascunhos, limites da janela e entradas duplicadas.
- `MoodleRestClientTest`: parâmetros REST e mapeamento das respostas do Moodle.
- `VerificacaoSchedulerTest`: sequência de chamadas e publicação; falhas de Moodle não publicam solicitações.
- `SolicitacaoPublisherTest` e `SolicitacaoReengajamentoTest`: exchange/routing key, chave estável e JSON ISO-8601.
- `AcompanhamentoApplicationTests`: inicialização do contexto.

O módulo `notificacoes` cobre:

- `NotificacaoServiceTest`: envio bem-sucedido, solicitação repetida, registro de erro e retry vencido.
- `NotificacaoRepositoryTest`: unicidade persistente, prazo mínimo para retry e limite de tentativas, usando H2 em memória.
- `SmtpNotificadorTest`: destinatário, assunto e conteúdo do lembrete.

Os testes unitários não substituem a demonstração integrada com RabbitMQ e Mailpit das seções anteriores.

## 8. Executar serviços fora do Docker (opcional)

Mantenha Moodle, RabbitMQ e Mailpit ativos pelo Compose. Para executar o serviço notificações diretamente na máquina, use host local e banco em arquivo:

```bash
cd notificacoes
mkdir -p data
export RABBITMQ_HOST=localhost RABBITMQ_PORT=5672 RABBITMQ_USER=reengajamento RABBITMQ_PASS=reengajamento
export SMTP_HOST=localhost SMTP_PORT=1025 NOTIFICACOES_DB_URL='jdbc:h2:file:./data/notificacoes'
mvn spring-boot:run
```

Para o acompanhamento local, pare primeiro o contêiner `acompanhamento` e configure `MOODLE_TOKEN`, `MOODLE_CURSO_ID`, `MOODLE_URL=http://localhost:8080`, `MOODLE_URL_PUBLICA=http://localhost:8080`, `RABBITMQ_HOST=localhost` e `ACOMPANHAMENTO_INTERVALO=PT1M`. Rode `mvn spring-boot:run` a partir de `acompanhamento/`. A porta pode ser alterada com `SERVER_PORT=8082`, pois o Compose reserva a 8081.

Para usar um SMTP real, substitua `SMTP_HOST`, `SMTP_PORT` e as credenciais no ambiente; habilite `SMTP_AUTH=true` e `SMTP_STARTTLS=true` conforme os requisitos do provedor. Não use senhas reais no repositório.

## 9. Solução de problemas

- **Moodle ainda não responde:** a primeira inicialização pode levar alguns minutos. Consulte `docker compose logs -f moodle`.
- **Token inválido:** o token é aplicado no Moodle a cada início do contêiner. Confirme que o `MOODLE_TOKEN` do `.env` não mudou depois da criação e rode `docker compose up -d moodle` para reaplicá-lo (ou `bash moodle/setup.sh`).
- **E-mail não aparece:** confira `docker compose logs notificacoes`, a caixa do Mailpit e se o SMTP está configurado como `mailpit:1025` para execução no Compose.
- **Acompanhamento mostra erro de conexão com RabbitMQ:** espere o healthcheck do RabbitMQ concluir e consulte `docker compose ps`.
- **Começar do zero:** `docker compose down -v` remove os volumes de Moodle e o histórico de idempotência do notificações; depois repita o setup.
