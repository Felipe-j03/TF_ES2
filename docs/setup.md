# Guia completo: requisitos, execução e testes

Este guia cobre o fluxo inteiro: Moodle → acompanhamento → RabbitMQ → notificações → SMTP/Mailpit. O ambiente local usa apenas contas fictícias e não envia e-mails para alunos reais.

## 1. Requisitos

### Para rodar o sistema completo

- Docker Desktop ou Docker Engine com o plugin Docker Compose.
- Pelo menos 4 GB de memória disponíveis para o Docker; o Moodle sozinho pode usar cerca de 1,5 GB.
- Git Bash no Windows para executar `moodle/setup.sh`.

### Para rodar os testes automatizados ou executar um serviço fora do Docker

- JDK 21.
- Maven 3.9 ou superior.

Os testes automatizados usam dependências locais de teste e não precisam que Moodle, RabbitMQ ou Mailpit estejam ativos.

## 2. Preparar a configuração

Na raiz do repositório, copie o arquivo de exemplo:

```bash
cp .env.example .env
```

Para a demonstração, mude `ACOMPANHAMENTO_INTERVALO` para `PT1M`. O padrão `PT1H` executa a consulta a cada hora. Os prazos das tarefas fictícias são calculados a partir do horário em que o script de configuração roda.

O Mailpit é o SMTP local: `SMTP_HOST=mailpit`, `SMTP_PORT=1025`, sem autenticação. Os e-mails aparecem em [http://localhost:8025](http://localhost:8025). O serviço não usa o SMTP próprio do Moodle.

## 3. Subir os serviços de apoio

Na raiz do projeto:

```bash
docker compose up -d moodle-db moodle rabbitmq mailpit
docker compose logs -f moodle
```

Na primeira inicialização, aguarde o Moodle concluir a instalação. Saia do acompanhamento de logs com Ctrl+C quando ele estiver pronto para receber requisições.

| Componente | Endereço | Acesso local de demonstração |
|---|---|---|
| Moodle | [http://localhost:8080](http://localhost:8080) | Usuário e senha definidos no `.env` |
| RabbitMQ | [http://localhost:15672](http://localhost:15672) | Usuário e senha definidos no `.env` |
| Mailpit | [http://localhost:8025](http://localhost:8025) | Sem login |

As portas do RabbitMQ podem ser alteradas em `RABBITMQ_PORT` e `RABBITMQ_UI_PORT` no `.env`.

## 4. Criar Moodle de demonstração

Execute:

```bash
bash moodle/setup.sh
```

O script habilita REST, cria a conta de serviço e o serviço externo do Moodle, matricula quatro alunos fictícios e prepara seis tarefas. Ele pode ser executado novamente: reaproveita os registros e atualiza os prazos relativos ao momento da execução.

No final, copie as duas linhas impressas para o `.env`:

```text
MOODLE_TOKEN=...
MOODLE_CURSO_ID=...
```

Salve o arquivo antes de iniciar o acompanhamento. O token é local e não deve ser publicado.

| Tarefa preparada | Resultado esperado |
|---|---|
| Pendente, vence em 2 horas | Um lembrete para cada um dos quatro alunos |
| Fora da janela, vence em 3 dias | Nenhum lembrete |
| Sem prazo | Nenhum lembrete |
| Já vencida | Nenhum lembrete |
| Vence em 2 horas, com entrega do aluno1 | Três lembretes; o aluno1 não recebe |
| Em grupo, vence em 2 horas | Nenhum lembrete |

## 5. Iniciar o fluxo completo

Construa e inicie os dois microsserviços:

```bash
docker compose up -d --build acompanhamento notificacoes
docker compose logs -f acompanhamento notificacoes
```

O acompanhamento espera 10 segundos antes da primeira verificação e depois usa o intervalo configurado. No `.env` de demonstração (`PT1M`), a primeira verificação deverá publicar solicitações em até alguns segundos. As demais verificações reapresentam a mesma chave por aluno/tarefa; o serviço de notificações ignora as que já foram enviadas.

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

Faça este cenário em um ambiente de demonstração novo, antes de as solicitações serem concluídas. Outra opção é recriar o ambiente inteiro com `docker compose down -v` e repetir as seções 3 e 4; esse comando apaga os dados locais do Moodle e do notificações.

Com o acompanhamento ainda parado, inicie o consumidor e interrompa o SMTP:

```bash
docker compose up -d --build notificacoes
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
- **Token inválido:** execute novamente `bash moodle/setup.sh` e atualize `MOODLE_TOKEN` no `.env`.
- **E-mail não aparece:** confira `docker compose logs notificacoes`, a caixa do Mailpit e se o SMTP está configurado como `mailpit:1025` para execução no Compose.
- **Acompanhamento mostra erro de conexão com RabbitMQ:** espere o healthcheck do RabbitMQ concluir e consulte `docker compose ps`.
- **Começar do zero:** `docker compose down -v` remove os volumes de Moodle e o histórico de idempotência do notificações; depois repita o setup.
