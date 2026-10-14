# CT23: rpcSendRawTransaction

| Linha | Valor |
|---|---|
| Fronteira | JSON-RPC |
| Quem chama | Blockchain Service |
| Quem responde | Provedor RPC |
| Atende | E8 |
| Como chamar | JSON-RPC, método `eth_sendRawTransaction` |

Envia à rede a transação já assinada pelo Blockchain Service. A chave privada nunca é enviada ao provedor.

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| params[0] (transação assinada) | texto (hexadecimal) | sim | 0xf86c0785... |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| result | texto (hash da transação) | sim | 0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060 |

## Erros

| Quando | O que volta |
|---|---|
| Transação recusada pela rede (saldo insuficiente, nonce inválido, gas insuficiente) | Objeto `error` do JSON-RPC. A task vai para `FAILED`, `failureReason` REJECTED_BY_NETWORK |
| Provedor fora do ar, tempo esgotado ou limite excedido | Falha de conexão ou HTTP 429/5xx. A task vai para `FAILED`, `failureReason` INTERNAL_ERROR |
