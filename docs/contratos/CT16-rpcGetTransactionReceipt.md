# CT16: rpcGetTransactionReceipt

| Linha | Valor |
|---|---|
| Fronteira | JSON-RPC |
| Quem chama | Blockchain Service |
| Quem responde | Provedor RPC |
| Atende | E5 e E9 |
| Como chamar | JSON-RPC, método `eth_getTransactionReceipt` |

Usado para saber se a transação foi incluída em um bloco e se foi executada com sucesso (premissa P3: uma confirmação).

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| params[0] (hash da transação) | texto | sim | 0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060 |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| result | objeto (Receipt) | não (nulo enquanto a transação não foi incluída em um bloco) | recibo da transação |

Campos de `objeto (Receipt)` usados pelo sistema:

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| status | texto (hexadecimal; 0x1 sucesso, 0x0 revertida) | sim | 0x1 |
| blockNumber | texto (hexadecimal) | sim | 0x5f3a21 |

## Erros

| Quando | O que volta |
|---|---|
| Transação ainda não incluída em bloco | `result` nulo (não é erro). A task segue `PENDING` |
| `status` igual a 0x0 | A task vai para `FAILED`, `failureReason` TRANSACTION_REVERTED |
| Provedor fora do ar, tempo esgotado ou limite excedido | Falha de conexão ou HTTP 429/5xx. A task segue `PENDING` e a verificação é repetida |

Nota (emenda do H08.1, decidida pelo responsável do módulo (Igor)): o `status` do recibo precisa ser `0x1` ou `0x0`. Recibo sem `status`, com outro valor ou sem `blockNumber` legível é tratado como resposta ilegível: a task segue `PENDING` e a verificação é repetida, e nunca vira `CONFIRMED`. Erro JSON-RPC (objeto `error`) também mantém a task `PENDING`. Sem recibo, o acompanhamento consulta CT24 `rpcGetTransactionByHash`.
