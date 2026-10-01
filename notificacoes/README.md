# Microsserviço de notificações

Ainda não implementado. Este arquivo descreve o que o serviço precisa fazer e o que ele recebe do acompanhamento.

## Responsabilidades

- Consumir as solicitações da fila `reengajamento.solicitacoes`.
- Montar o lembrete (nome da tarefa, prazo e link) e enviar por SMTP.
- Registrar o resultado de cada envio, inclusive as falhas.
- Não reenviar uma solicitação que já foi concluída. A chave para isso é o campo `idempotencyKey`, que identifica curso, tarefa e aluno.
- Tentar de novo quando o envio falhar.

Os critérios de aceite 3 ("nenhum novo envio ao reprocessar uma solicitação concluída") e 4 ("registro de falhas e nova tentativa de processamento") dependem deste serviço.

## Fila

| Item | Valor |
|---|---|
| Exchange | `reengajamento` (direct, durável) |
| Fila | `reengajamento.solicitacoes` (durável) |
| Routing key | `solicitacao` |

O acompanhamento declara a exchange, a fila e o binding ao subir. Declarar de novo no notificações, com os mesmos parâmetros, não causa problema.

## Mensagem

JSON, com datas em ISO-8601 (UTC):

```json
{
  "idempotencyKey": "2:1:4",
  "cursoId": 2,
  "cursoNome": "ES2 - Demonstração",
  "tarefaId": 1,
  "tarefaNome": "Tarefa pendente (vence em 2h)",
  "prazo": "2026-10-01T15:31:38Z",
  "linkTarefa": "http://localhost:8080/mod/assign/view.php?id=1",
  "alunoId": 4,
  "alunoNome": "Aluno Teste 1",
  "alunoEmail": "aluno1@example.com",
  "geradoEm": "2026-10-01T13:31:38Z"
}
```

A `idempotencyKey` segue o formato `cursoId:tarefaId:alunoId`. Como o acompanhamento roda de hora em hora, a mesma chave pode chegar mais de uma vez enquanto a tarefa estiver na janela de 24 horas. É responsabilidade deste serviço enviar só uma vez.

O Spring AMQP do acompanhamento adiciona o header `__TypeId__` com o nome da classe Java de origem (`br.pucrs.es2.acompanhamento.mensageria.SolicitacaoReengajamento`). Se o notificações for feito em Spring e tiver a própria classe para a mensagem, configure o conversor para usar o tipo do parâmetro do listener em vez do header (`TypePrecedence.INFERRED`).

## SMTP nos testes

O Mailpit já sobe no `docker-compose.yml`:

- de dentro da rede do compose: host `mailpit`, porta `1025`, sem autenticação;
- rodando na máquina: `localhost:1025`;
- os e-mails recebidos aparecem em http://localhost:8025.
