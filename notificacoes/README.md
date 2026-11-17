# Microsserviço de notificações

Consome solicitações de `reengajamento.solicitacoes`, envia lembretes por SMTP e mantém um registro H2 persistente por `idempotencyKey`.

## Comportamento

- O e-mail inclui nome do aluno, tarefa, curso, prazo no horário de Brasília e link para a tarefa.
- O estado `ENVIADO` impede que uma solicitação concluída seja enviada de novo, inclusive após reinício, enquanto o volume `notificacoes-data` for preservado.
- Falhas SMTP são persistidas com erro, quantidade de tentativas e horário da próxima tentativa. O scheduler reprocessa falhas vencidas.
- `NOTIFICACOES_MAX_TENTATIVAS` limita o total de envios, contando a primeira tentativa. O padrão é 5.
- `NOTIFICACOES_INTERVALO_RETENTATIVA` define o intervalo entre retries. O padrão é 30 segundos.
- Conteúdo de mensagem fica salvo enquanto há retry; ao concluir, o payload é apagado e ficam chave, estado, tentativas e horário.

## Contrato RabbitMQ

| Item | Valor |
|---|---|
| Exchange | `reengajamento` (direct, durável) |
| Fila | `reengajamento.solicitacoes` (durável) |
| Routing key | `solicitacao` |

Datas são serializadas em ISO-8601/UTC. A chave usa o formato `cursoId:tarefaId:alunoId`; solicitações repetidas podem ser publicadas pelo acompanhamento a cada ciclo.

O produtor envia o header `__TypeId__` com seu próprio nome de classe. O consumidor configura `TypePrecedence.INFERRED` para converter o JSON ao record local usado pelo listener.

## Configuração

No Docker Compose, o SMTP aponta para Mailpit (`mailpit:1025`) e o H2 usa o volume `/data`. Para execução local, use `localhost:1025` e um caminho de banco em arquivo. A configuração SMTP pode ser substituída por variáveis de ambiente `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH` e `SMTP_STARTTLS`.

## Testar

Testes sem serviços externos:

```bash
mvn -f notificacoes/pom.xml test
```

Para o fluxo integrado, siga o guia em [../docs/setup.md](../docs/setup.md), que inicia Moodle, RabbitMQ, este serviço e Mailpit e descreve como verificar deduplicação e retries.
