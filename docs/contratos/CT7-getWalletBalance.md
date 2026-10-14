# CT7: getWalletBalance

| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Core API |
| Quem responde | Blockchain Service |
| Atende | E3 |
| Como chamar | `GET /wallets/{ethereumAddress}/balance` |

Consulta o saldo na rede por CT8 `rpcGetBalance` e converte de wei para ETH em decimal.

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| X-Service-Key (cabeçalho) | texto | sim | dev-service-key |
| ethereumAddress (caminho) | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |

## Saída

Resposta `200`.

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| ethereumAddress | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
| balanceEth | decimal | sim | 0.25 |
| network | um de: SEPOLIA | sim | SEPOLIA |

## Erros

| Quando | O que volta |
|---|---|
| `X-Service-Key` ausente ou incorreta | HTTP 401, `code` SERVICE_KEY_INVALID |
| `ethereumAddress` com formato inválido | HTTP 400, `code` ADDRESS_INVALID |
| `ethereumAddress` sem carteira em CT5 `wallet` | HTTP 404, `code` WALLET_NOT_FOUND |
| Provedor RPC indisponível ou com falha | HTTP 502, `code` RPC_UNAVAILABLE |
| Falha inesperada, incluindo Banco do Blockchain Service indisponível | HTTP 500, `code` INTERNAL_ERROR |
