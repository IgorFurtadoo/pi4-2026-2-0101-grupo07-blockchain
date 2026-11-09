# CT15: notifyTaskCompleted

| Linha | Valor |
|---|---|
| Fronteira | HTTP (webhook) |
| Quem chama | Blockchain Service |
| Quem responde | Core API |
| Atende | E5 e E9 |
| Como chamar | `POST /webhooks/tasks` |

Notificação rápida de que uma task chegou ao estado final. Pela regra de local dos contratos HTTP, a ficha fica no módulo que responde (Core API). A política de retentativa em caso de falha do webhook continua em aberto (premissa P4). Se o webhook não chegar, o Core API recupera o estado por CT14 `getTask`. Reenviar a mesma notificação é seguro: o Core API responde `204` sem alterar nada.

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| X-Service-Key (cabeçalho) | texto | sim | dev-service-key |
| taskId (corpo) | texto | sim | a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b |
| status (corpo) | um de: CONFIRMED, FAILED | sim | CONFIRMED |
| transactionHash (corpo) | texto | não (ausente se a transação nunca chegou a ser enviada) | 0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060 |
| failureReason (corpo) | um de: REJECTED_BY_NETWORK, TRANSACTION_REVERTED, INTERNAL_ERROR | não (obrigatório quando `status` é FAILED) | REJECTED_BY_NETWORK |
| updatedAt (corpo) | data e hora | sim | 2026-10-20T14:36:10Z |

## Saída

Saída: não tem (resposta `204`, sem corpo).

## Erros

| Quando | O que volta |
|---|---|
| `X-Service-Key` ausente ou incorreta | HTTP 401, `code` SERVICE_KEY_INVALID |
| Corpo com campo obrigatório ausente ou `status` fora dos valores aceitos | HTTP 400, `code` REQUEST_INVALID |
| `taskId` não existe em CT12 `eth_transaction` (por exemplo, a notificação chegou antes de o Core API gravar a transação) | HTTP 404, `code` TRANSACTION_NOT_FOUND. O Core API recupera o estado depois por CT14 `getTask` |
| Falha inesperada, incluindo Banco do Core API indisponível | HTTP 500, `code` INTERNAL_ERROR |

Nota (emenda do H08.1, decidida pelo responsável do módulo (Igor)): o Blockchain Service envia esta notificação em **toda** transição de uma task para estado final: `CONFIRMED`, `FAILED`/TRANSACTION_REVERTED, `FAILED`/REJECTED_BY_NETWORK e `FAILED`/INTERNAL_ERROR, inclusive quando a transação nunca chegou a ser enviada (sem `transactionHash`). O envio acontece depois de gravar o estado final em CT11, com a mesma `X-Service-Key` (P2) usada nas chamadas da Core API ao Blockchain Service, e o corpo tem os mesmos campos do CT14. Não há retentativa (P4 em aberto): qualquer resposta fora de 2xx, tempo esgotado (5 s) ou falha de conexão só vai ao log, e a Core API recupera o estado por CT14 `getTask`. 404 TRANSACTION_NOT_FOUND é a corrida conhecida da seção 7.5 do desenho.
