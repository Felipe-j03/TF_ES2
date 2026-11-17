# Registro dos testes

Este documento registra a execução dos testes do serviço de reengajamento: os critérios de aceite, executados de ponta a ponta com contas fictícias e Mailpit, e os testes automatizados. Para reproduzir, siga o [guia de instalação e uso](setup.md).

## 1. Ambiente

| Item | Valor |
|---|---|
| Data da execução | 16/11/2026 |
| Sistema | Windows 11, Docker Desktop (Docker 29.8.0) |
| Moodle | `erseco/alpine-moodle:v5.2.3` com PostgreSQL 16 |
| Mensageria / SMTP | RabbitMQ 4 e Mailpit |
| Microsserviços | Java 21 (JDK 21.0.3 local; imagem `eclipse-temurin:21`), Spring Boot 4.1.1 |
| Configuração | `.env.example` com `ACOMPANHAMENTO_INTERVALO=PT1M` (o arquivo traz `PT1H`; o intervalo curto serve à demonstração); retry a cada 30 s, até 5 tentativas |
| Estado inicial | Ambiente criado do zero (`docker compose down -v` e depois `docker compose up -d --build`) |

## 2. Dados de teste

Criados automaticamente por `moodle/setup.php`: disciplina `ES2DEMO` (id 2), alunos `aluno1` a `aluno4` e seis tarefas, com prazos relativos ao momento da execução.

| Tarefa | Prazo | Situação | Lembretes esperados |
|---|---|---|---|
| Tarefa pendente (vence em 2h) | agora + 2 h | ninguém entregou | 4 (aluno1 a aluno4) |
| Tarefa fora da janela (3 dias) | agora + 3 dias | fora da janela de 24 h | 0 |
| Tarefa sem prazo | nenhum | sem prazo | 0 |
| Tarefa já vencida | agora - 1 dia | vencida | 0 |
| Tarefa com entrega (vence em 2h) | agora + 2 h | aluno1 entregou (`submitted`) | 3 (aluno2 a aluno4) |
| Tarefa em grupo (vence em 2h) | agora + 2 h | entrega em grupo | 0 |

Total esperado: **7 e-mails**.

## 3. Critérios de aceite

### Critério 1: um lembrete para tarefa pendente dentro da janela de 24 horas

- **Procedimento:** subir o ambiente com `docker compose up -d --build` e aguardar o primeiro ciclo do acompanhamento.
- **Resultado esperado:** um e-mail para cada aluno na "Tarefa pendente (vence em 2h)".
- **Resultado obtido:** o log do acompanhamento registrou `Verificação do curso 2: 6 tarefas, 4 alunos, 1 entregas, 7 solicitações publicadas`. O Mailpit recebeu 4 e-mails de assunto `Lembrete: Tarefa pendente (vence em 2h)`, para aluno1@example.com, aluno2@example.com, aluno3@example.com e aluno4@example.com.
- **Situação:** aprovado.

### Critério 2: nenhum lembrete para tarefa já enviada ou fora da janela

- **Procedimento:** conferir a caixa do Mailpit depois do primeiro ciclo.
- **Resultado esperado:** o aluno1 não recebe lembrete da tarefa em que já entregou; não há e-mail das tarefas fora da janela, sem prazo, vencida ou em grupo.
- **Resultado obtido:** a tarefa "Tarefa com entrega (vence em 2h)" gerou 3 e-mails (aluno2, aluno3 e aluno4) e nenhum para o aluno1. Não chegou nenhum e-mail das outras quatro tarefas. Total na caixa: **7**.
- **Situação:** aprovado.

### Critério 3: nenhum novo envio ao reprocessar uma solicitação concluída

- **Procedimento:** com os 7 e-mails já enviados, executar `docker compose restart acompanhamento notificacoes` e aguardar um novo ciclo de verificação (o acompanhamento republica as mesmas 7 solicitações a cada ciclo).
- **Resultado esperado:** o notificações ignora as solicitações repetidas, e o Mailpit permanece com 7 e-mails.
- **Resultado obtido:** o log do notificações registrou 7 linhas `Solicitação ... ignorada: já enviada, em processamento ou aguardando nova tentativa` (por exemplo, `2:5:6` e `2:5:7`). O Mailpit permaneceu com **7** e-mails, mesmo depois do reinício, porque o estado `ENVIADO` fica no banco H2 do volume `notificacoes-data`.
- **Situação:** aprovado.

### Critério 4: registro de falhas e nova tentativa de processamento

- **Procedimento:** em ambiente novo, subir Moodle, RabbitMQ, Mailpit e notificações; parar o Mailpit (`docker compose stop mailpit`); iniciar o acompanhamento; observar o log; religar o Mailpit (`docker compose start mailpit`).
- **Resultado esperado:** falhas de envio registradas com o número da tentativa; após o intervalo de retry, os e-mails são enviados.
- **Resultado obtido:** com o SMTP parado, o log registrou 7 falhas, no formato `Falha no envio da solicitação 2:1:4 (tentativa 1/5); próxima tentativa em 2026-11-16T15:28:05Z. Tipo: MailSendException`. Após religar o Mailpit, o `RetentativaScheduler` reprocessou as solicitações e os 7 e-mails chegaram (`Lembrete enviado para a solicitação 2:1:4 (tentativa 2)`, cerca de 30 s depois da falha). O Mailpit terminou com **7** e-mails, sem duplicatas.
- **Situação:** aprovado.

## 4. Testes automatizados

Executados com `mvn -f <módulo>/pom.xml test`, sem Docker. Resultado em 16/11/2026: **30 testes, 0 falhas, 0 erros** (`BUILD SUCCESS` nos dois módulos).

### acompanhamento: 21 testes

| Classe | Testes | O que cobre |
|---|---|---|
| `DetectorPendenciasTest` | 10 | janela de 24 h e seus limites, prazo ausente ou vencido, tarefa em grupo, entrega `submitted`, rascunho, entradas duplicadas |
| `MoodleRestClientTest` | 5 | mapeamento de prazo, link e curso das tarefas, filtro de alunos por permissão de envio, junção das entregas de várias tarefas, nenhuma chamada sem tarefas, erro do Moodle convertido em `MoodleException` |
| `VerificacaoSchedulerTest` | 2 | sequência de chamadas e publicação; falha do Moodle não publica solicitações |
| `SolicitacaoReengajamentoTest` | 2 | chave de idempotência estável e JSON com datas ISO-8601 |
| `SolicitacaoPublisherTest` | 1 | exchange e routing key da publicação |
| `AcompanhamentoApplicationTests` | 1 | inicialização do contexto |

### notificacoes: 9 testes

| Classe | Testes | O que cobre |
|---|---|---|
| `NotificacaoServiceTest` | 5 | envio bem-sucedido, solicitação já concluída não reenviada, falha com nova tentativa agendada, falha na última tentativa sem novo retry, reprocessamento de falha vencida |
| `NotificacaoRepositoryTest` | 3 | chave enviada não pode ser reivindicada de novo, falha só é reivindicada no horário do retry, falha sem nova tentativa não volta para a fila de retry (H2 em memória) |
| `SmtpNotificadorTest` | 1 | destinatário, assunto e conteúdo do lembrete |

## 5. Problema encontrado durante os testes

Ao montar o ambiente com um único `docker compose up`, a imagem do `notificacoes` falhou na compilação: `package tools.jackson.databind.json does not exist`. O `pom.xml` não declarava o Jackson, que os testes recebiam de forma transitiva pelo starter de teste, por isso os testes automatizados passavam e o erro só aparecia no build do Docker. A correção foi declarar `spring-boot-starter-jackson` no `pom.xml` do módulo. Após a correção, todos os cenários acima foram executados.

## 6. Limitações conhecidas

- Os testes automatizados são unitários; o fluxo com RabbitMQ e SMTP reais é verificado manualmente, conforme a seção 3.
- `SolicitacaoConsumer` e `RetentativaScheduler` não têm teste unitário próprio; são exercitados pelos cenários dos critérios 3 e 4.
- Uma solicitação que atinge o limite de tentativas permanece registrada como `FALHOU`, sem alerta e sem nova tentativa automática.
- O prazo no e-mail é exibido em UTC.
