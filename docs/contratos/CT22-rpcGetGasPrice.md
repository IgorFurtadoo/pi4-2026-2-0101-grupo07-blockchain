# CT22: rpcGetGasPrice

| Linha | Valor |
|---|---|
| Fronteira | JSON-RPC |
| Quem chama | Blockchain Service |
| Quem responde | Provedor RPC |
| Atende | E8 |
| Como chamar | JSON-RPC, método `eth_gasPrice` |

Obtém o preço do gas em wei, usado para montar a transação antes de assiná-la.

## Entrada

Entrada: não tem (`params` é uma lista vazia).

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| result | texto (preço do gas em wei, hexadecimal) | sim | 0x3b9aca00 |

## Erros

| Quando | O que volta |
|---|---|
| Provedor fora do ar, tempo esgotado ou limite excedido | Falha de conexão ou HTTP 429/5xx. A task vai para `FAILED`, `failureReason` INTERNAL_ERROR |
