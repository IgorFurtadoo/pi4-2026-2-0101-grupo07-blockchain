# CT21: rpcGetTransactionCount

| Linha | Valor |
|---|---|
| Fronteira | JSON-RPC |
| Quem chama | Blockchain Service |
| Quem responde | Provedor RPC |
| Atende | E8 |
| Como chamar | JSON-RPC, método `eth_getTransactionCount` |

Obtém o nonce da conta, necessário para montar a transação antes de assiná-la.

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| params[0] (endereço) | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
| params[1] (bloco) | texto | sim | pending |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| result | texto (nonce, hexadecimal) | sim | 0x7 |

## Erros

| Quando | O que volta |
|---|---|
| Endereço com formato inválido | Objeto `error` do JSON-RPC (código -32602). A task vai para `FAILED`, `failureReason` INTERNAL_ERROR |
| Provedor fora do ar, tempo esgotado ou limite excedido | Falha de conexão ou HTTP 429/5xx. A task vai para `FAILED`, `failureReason` INTERNAL_ERROR |
