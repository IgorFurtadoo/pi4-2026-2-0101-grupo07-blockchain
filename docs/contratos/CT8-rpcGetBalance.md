# CT8: rpcGetBalance

| Linha | Valor |
|---|---|
| Fronteira | JSON-RPC |
| Quem chama | Blockchain Service |
| Quem responde | Provedor RPC |
| Atende | E3 |
| Como chamar | JSON-RPC, método `eth_getBalance` |

O endpoint é um provedor público gratuito da Sepolia, configurado por variável de ambiente (premissa P8). Serviço externo: os nomes seguem o padrão do Ethereum JSON-RPC.

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| params[0] (endereço) | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
| params[1] (bloco) | texto | sim | latest |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| result | texto (saldo em wei, hexadecimal) | sim | 0x3782dace9d900000 |

## Erros

| Quando | O que volta |
|---|---|
| Endereço com formato inválido | Objeto `error` do JSON-RPC (código -32602). O Blockchain Service responde HTTP 502 em CT7 |
| Provedor fora do ar, tempo esgotado ou limite do provedor excedido | Falha de conexão ou HTTP 429/5xx. O Blockchain Service responde HTTP 502, `code` RPC_UNAVAILABLE em CT7 |
