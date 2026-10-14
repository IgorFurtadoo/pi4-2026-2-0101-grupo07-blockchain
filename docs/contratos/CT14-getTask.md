# CT14: getTask

| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Core API |
| Quem responde | Blockchain Service |
| Atende | E5 e E9 |
| Como chamar | `GET /tasks/{taskId}` |

Consulta ativa do Core API. Serve de plano B quando o webhook CT15 não chega (premissa P4). Enquanto a task está `PENDING`, o Blockchain Service pode verificar o recibo na rede (CT16) antes de responder.

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| X-Service-Key (cabeçalho) | texto | sim | dev-service-key |
| taskId (caminho) | texto | sim | a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b |

## Saída

Resposta `200`.

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| taskId | texto | sim | a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b |
| status | um de: PENDING, CONFIRMED, FAILED | sim | CONFIRMED |
| transactionHash | texto | não (ausente antes do envio à rede) | 0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060 |
| failureReason | um de: REJECTED_BY_NETWORK, TRANSACTION_REVERTED, INTERNAL_ERROR | não (só quando `status` é FAILED) | REJECTED_BY_NETWORK |
| updatedAt | data e hora | sim | 2026-10-20T14:36:10Z |

## Erros

| Quando | O que volta |
|---|---|
| `X-Service-Key` ausente ou incorreta | HTTP 401, `code` SERVICE_KEY_INVALID |
| `taskId` inexistente | HTTP 404, `code` TASK_NOT_FOUND |
| Falha inesperada, incluindo Banco do Blockchain Service indisponível | HTTP 500, `code` INTERNAL_ERROR |
