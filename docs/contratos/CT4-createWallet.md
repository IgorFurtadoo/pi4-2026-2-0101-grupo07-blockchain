# CT4: createWallet

| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Core API |
| Quem responde | Blockchain Service |
| Atende | E2 |
| Como chamar | `POST /wallets` |

Gera um par de chaves Ethereum, grava a chave privada cifrada em CT5 `wallet` e devolve apenas o endereço público (premissas P6 e P7). A chave privada nunca sai do Blockchain Service.

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| X-Service-Key (cabeçalho) | texto | sim | dev-service-key |

## Saída

Resposta `201`.

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| ethereumAddress | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |

## Erros

| Quando | O que volta |
|---|---|
| `X-Service-Key` ausente ou incorreta | HTTP 401, `code` SERVICE_KEY_INVALID |
| Falha inesperada, incluindo Banco do Blockchain Service indisponível | HTTP 500, `code` INTERNAL_ERROR |
